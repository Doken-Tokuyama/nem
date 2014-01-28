import java.nio.*;
import java.util.concurrent.*;
import org.json.simple.*;
import java.io.*;
import java.net.*;
import java.util.*;

static class Peer implements Comparable<Peer>
{
    static final int STATE_NONCONNECTED = 0;
    static final int STATE_CONNECTED = 1;
    static final int STATE_DISCONNECTED = 2;
    final int index;
    String platform;
    String announcedAddress;
    boolean shareAddress;
    String hallmark;
    long accountId;
    int weight;
    int date;
    long adjustedWeight;
    String application;
    String version;
    long blacklistingTime;
    int state;
    long downloadedVolume;
    long uploadedVolume;
    
    Peer(final String announcedAddress, final int index) {
        super();
        this.announcedAddress = announcedAddress;
        this.index = index;
    }
    
    static Peer addPeer(final String address, String announcedAddress) {
        try {
            final URL url = new URL("http://" + address);
        }
        catch (MalformedURLException e) {
            Nxt.logDebugMessage("malformed peer address " + address, e);
            return null;
        }
        try {
            final URL url2 = new URL("http://" + announcedAddress);
        }
        catch (MalformedURLException e) {
            Nxt.logDebugMessage("malformed peer announced address " + announcedAddress, e);
            announcedAddress = "";
        }
        if (address.equals("localhost") || address.equals("127.0.0.1") || address.equals("0:0:0:0:0:0:0:1")) {
            return null;
        }
        if (Nxt.myAddress != null && Nxt.myAddress.length() > 0 && Nxt.myAddress.equals(announcedAddress)) {
            return null;
        }
        Peer peer = Nxt.peers.get((announcedAddress.length() > 0) ? announcedAddress : address);
        if (peer == null) {
            peer = new Peer(announcedAddress, Nxt.peerCounter.incrementAndGet());
            Nxt.peers.put((announcedAddress.length() > 0) ? announcedAddress : address, peer);
        }
        return peer;
    }
    
    boolean analyzeHallmark(final String realHost, final String hallmark) {
        if (hallmark == null) {
            return true;
        }
        try {
            byte[] hallmarkBytes;
            try {
                hallmarkBytes = Nxt.convert(hallmark);
            }
            catch (NumberFormatException e2) {
                return false;
            }
            final ByteBuffer buffer = ByteBuffer.wrap(hallmarkBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            final byte[] publicKey = new byte[32];
            buffer.get(publicKey);
            final int hostLength = buffer.getShort();
            final byte[] hostBytes = new byte[hostLength];
            buffer.get(hostBytes);
            final String host = new String(hostBytes, "UTF-8");
            if (host.length() > 100 || !host.equals(realHost)) {
                return false;
            }
            final int weight = buffer.getInt();
            if (weight <= 0 || weight > 1000000000L) {
                return false;
            }
            final int date = buffer.getInt();
            buffer.get();
            final byte[] signature = new byte[64];
            buffer.get(signature);
            final byte[] data = new byte[hallmarkBytes.length - 64];
            System.arraycopy(hallmarkBytes, 0, data, 0, data.length);
            if (Crypto.verify(signature, data, publicKey)) {
                this.hallmark = hallmark;
                final long accountId = Account.getId(publicKey);
                final LinkedList<Peer> groupedPeers = new LinkedList<Peer>();
                int validDate = 0;
                this.accountId = accountId;
                this.weight = weight;
                this.date = date;
                for (final Peer peer : Nxt.peers.values()) {
                    if (peer.accountId == accountId) {
                        groupedPeers.add(peer);
                        if (peer.date <= validDate) {
                            continue;
                        }
                        validDate = peer.date;
                    }
                }
                long totalWeight = 0L;
                for (final Peer peer2 : groupedPeers) {
                    if (peer2.date == validDate) {
                        totalWeight += peer2.weight;
                    }
                    else {
                        peer2.weight = 0;
                    }
                }
                for (final Peer peer2 : groupedPeers) {
                    peer2.adjustedWeight = 1000000000L * peer2.weight / totalWeight;
                    peer2.updateWeight();
                }
                return true;
            }
        }
        catch (RuntimeException | UnsupportedEncodingException e) {
            Nxt.logDebugMessage("Failed to analyze hallmark for peer " + realHost, e);
        }
        return false;
    }
    
    void blacklist() {
        this.blacklistingTime = System.currentTimeMillis();
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        final JSONArray removedKnownPeers = new JSONArray();
        final JSONObject removedKnownPeer = new JSONObject();
        removedKnownPeer.put((Object)"index", (Object)this.index);
        removedKnownPeers.add((Object)removedKnownPeer);
        response.put((Object)"removedKnownPeers", (Object)removedKnownPeers);
        final JSONArray addedBlacklistedPeers = new JSONArray();
        final JSONObject addedBlacklistedPeer = new JSONObject();
        addedBlacklistedPeer.put((Object)"index", (Object)this.index);
        addedBlacklistedPeer.put((Object)"announcedAddress", (Object)((this.announcedAddress.length() > 30) ? (this.announcedAddress.substring(0, 30) + "...") : this.announcedAddress));
        for (final String wellKnownPeer : Nxt.wellKnownPeers) {
            if (this.announcedAddress.equals(wellKnownPeer)) {
                addedBlacklistedPeer.put((Object)"wellKnown", (Object)true);
                break;
            }
        }
        addedBlacklistedPeers.add((Object)addedBlacklistedPeer);
        response.put((Object)"addedBlacklistedPeers", (Object)addedBlacklistedPeers);
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
    }
    
    @Override
    public int compareTo(final Peer o) {
        final long weight = this.getWeight();
        final long weight2 = o.getWeight();
        if (weight > weight2) {
            return -1;
        }
        if (weight < weight2) {
            return 1;
        }
        return this.index - o.index;
    }
    
    void connect() {
        final JSONObject request = new JSONObject();
        request.put((Object)"requestType", (Object)"getInfo");
        if (Nxt.myAddress != null && Nxt.myAddress.length() > 0) {
            request.put((Object)"announcedAddress", (Object)Nxt.myAddress);
        }
        if (Nxt.myHallmark != null && Nxt.myHallmark.length() > 0) {
            request.put((Object)"hallmark", (Object)Nxt.myHallmark);
        }
        request.put((Object)"application", (Object)"NRS");
        request.put((Object)"version", (Object)"0.5.10");
        request.put((Object)"platform", (Object)Nxt.myPlatform);
        request.put((Object)"scheme", (Object)Nxt.myScheme);
        request.put((Object)"port", (Object)Nxt.myPort);
        request.put((Object)"shareAddress", (Object)Nxt.shareMyAddress);
        final JSONObject response = this.send(request);
        if (response != null) {
            this.application = (String)response.get((Object)"application");
            this.version = (String)response.get((Object)"version");
            this.platform = (String)response.get((Object)"platform");
            this.shareAddress = Boolean.TRUE.equals(response.get((Object)"shareAddress"));
            if (this.analyzeHallmark(this.announcedAddress, (String)response.get((Object)"hallmark"))) {
                this.setState(1);
            }
        }
    }
    
    void deactivate() {
        if (this.state == 1) {
            this.disconnect();
        }
        this.setState(0);
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        final JSONArray removedActivePeers = new JSONArray();
        final JSONObject removedActivePeer = new JSONObject();
        removedActivePeer.put((Object)"index", (Object)this.index);
        removedActivePeers.add((Object)removedActivePeer);
        response.put((Object)"removedActivePeers", (Object)removedActivePeers);
        if (this.announcedAddress.length() > 0) {
            final JSONArray addedKnownPeers = new JSONArray();
            final JSONObject addedKnownPeer = new JSONObject();
            addedKnownPeer.put((Object)"index", (Object)this.index);
            addedKnownPeer.put((Object)"announcedAddress", (Object)((this.announcedAddress.length() > 30) ? (this.announcedAddress.substring(0, 30) + "...") : this.announcedAddress));
            for (final String wellKnownPeer : Nxt.wellKnownPeers) {
                if (this.announcedAddress.equals(wellKnownPeer)) {
                    addedKnownPeer.put((Object)"wellKnown", (Object)true);
                    break;
                }
            }
            addedKnownPeers.add((Object)addedKnownPeer);
            response.put((Object)"addedKnownPeers", (Object)addedKnownPeers);
        }
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
    }
    
    void disconnect() {
        this.setState(2);
    }
    
    static Peer getAnyPeer(final int state, final boolean applyPullThreshold) {
        final List<Peer> selectedPeers = new ArrayList<Peer>();
        for (final Peer peer : Nxt.peers.values()) {
            if (peer.blacklistingTime <= 0L && peer.state == state && peer.announcedAddress.length() > 0 && (!applyPullThreshold || !Nxt.enableHallmarkProtection || peer.getWeight() >= Nxt.pullThreshold)) {
                selectedPeers.add(peer);
            }
        }
        if (selectedPeers.size() > 0) {
            long totalWeight = 0L;
            for (final Peer peer2 : selectedPeers) {
                long weight = peer2.getWeight();
                if (weight == 0L) {
                    weight = 1L;
                }
                totalWeight += weight;
            }
            long hit = ThreadLocalRandom.current().nextLong(totalWeight);
            for (final Peer peer3 : selectedPeers) {
                long weight2 = peer3.getWeight();
                if (weight2 == 0L) {
                    weight2 = 1L;
                }
                if ((hit -= weight2) < 0L) {
                    return peer3;
                }
            }
        }
        return null;
    }
    
    static int getNumberOfConnectedPublicPeers() {
        int numberOfConnectedPeers = 0;
        for (final Peer peer : Nxt.peers.values()) {
            if (peer.state == 1 && peer.announcedAddress.length() > 0) {
                ++numberOfConnectedPeers;
            }
        }
        return numberOfConnectedPeers;
    }
    
    int getWeight() {
        if (this.accountId == 0L) {
            return 0;
        }
        final Account account = Nxt.accounts.get(this.accountId);
        if (account == null) {
            return 0;
        }
        return (int)(this.adjustedWeight * (account.getBalance() / 100L) / 1000000000L);
    }
    
    String getSoftware() {
        final StringBuilder buf = new StringBuilder();
        buf.append((this.application == null) ? "?" : this.application.substring(0, Math.min(this.application.length(), 10)));
        buf.append(" (");
        buf.append((this.version == null) ? "?" : this.version.substring(0, Math.min(this.version.length(), 10)));
        buf.append(")").append(" @ ");
        buf.append((this.platform == null) ? "?" : this.platform.substring(0, Math.min(this.platform.length(), 12)));
        return buf.toString();
    }
    
    void removeBlacklistedStatus() {
        this.setState(0);
        this.blacklistingTime = 0L;
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        final JSONArray removedBlacklistedPeers = new JSONArray();
        final JSONObject removedBlacklistedPeer = new JSONObject();
        removedBlacklistedPeer.put((Object)"index", (Object)this.index);
        removedBlacklistedPeers.add((Object)removedBlacklistedPeer);
        response.put((Object)"removedBlacklistedPeers", (Object)removedBlacklistedPeers);
        final JSONArray addedKnownPeers = new JSONArray();
        final JSONObject addedKnownPeer = new JSONObject();
        addedKnownPeer.put((Object)"index", (Object)this.index);
        addedKnownPeer.put((Object)"announcedAddress", (Object)((this.announcedAddress.length() > 30) ? (this.announcedAddress.substring(0, 30) + "...") : this.announcedAddress));
        for (final String wellKnownPeer : Nxt.wellKnownPeers) {
            if (this.announcedAddress.equals(wellKnownPeer)) {
                addedKnownPeer.put((Object)"wellKnown", (Object)true);
                break;
            }
        }
        addedKnownPeers.add((Object)addedKnownPeer);
        response.put((Object)"addedKnownPeers", (Object)addedKnownPeers);
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
    }
    
    void removePeer() {
        Nxt.peers.values().remove(this);
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        final JSONArray removedKnownPeers = new JSONArray();
        final JSONObject removedKnownPeer = new JSONObject();
        removedKnownPeer.put((Object)"index", (Object)this.index);
        removedKnownPeers.add((Object)removedKnownPeer);
        response.put((Object)"removedKnownPeers", (Object)removedKnownPeers);
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
    }
    
    static void sendToSomePeers(final JSONObject request) {
        request.put((Object)"protocol", (Object)1);
        final JSONStreamAware jsonStreamAware = (JSONStreamAware)new JSONStreamAware() {
            final char[] jsonChars = request.toJSONString().toCharArray();
            
            public void writeJSONString(final Writer out) throws IOException {
                out.write(this.jsonChars);
            }
        };
        int successful = 0;
        final List<Future<JSONObject>> expectedResponses = new ArrayList<Future<JSONObject>>();
        for (final Peer peer : Nxt.peers.values()) {
            if (Nxt.enableHallmarkProtection && peer.getWeight() < Nxt.pushThreshold) {
                continue;
            }
            if (peer.blacklistingTime == 0L && peer.state == 1 && peer.announcedAddress.length() > 0) {
                final Future<JSONObject> futureResponse = Nxt.sendToPeersService.submit((Callable<JSONObject>)new Callable<JSONObject>() {
                    @Override
                    public JSONObject call() {
                        return peer.send(jsonStreamAware);
                    }
                });
                expectedResponses.add(futureResponse);
            }
            if (expectedResponses.size() >= Nxt.sendToPeersLimit - successful) {
                for (final Future<JSONObject> future : expectedResponses) {
                    try {
                        final JSONObject response = future.get();
                        if (response == null || response.get((Object)"error") != null) {
                            continue;
                        }
                        ++successful;
                    }
                    catch (InterruptedException e2) {
                        Thread.currentThread().interrupt();
                    }
                    catch (ExecutionException e) {
                        Nxt.logDebugMessage("Error in sendToSomePeers", e);
                    }
                }
                expectedResponses.clear();
            }
            if (successful >= Nxt.sendToPeersLimit) {
                return;
            }
        }
    }
    
    JSONObject send(final JSONObject request) {
        request.put((Object)"protocol", (Object)1);
        return this.send((JSONStreamAware)new JSONStreamAware() {
            public void writeJSONString(final Writer out) throws IOException {
                request.writeJSONString(out);
            }
        });
    }
    
    JSONObject send(final JSONStreamAware request) {
        String log = null;
        boolean showLog = false;
        HttpURLConnection connection = null;
        JSONObject response;
        try {
            if (Nxt.communicationLoggingMask != 0) {
                log = "\"" + this.announcedAddress + "\": " + request.toString();
            }
            final URL url = new URL("http://" + this.announcedAddress + ((new URL("http://" + this.announcedAddress).getPort() < 0) ? ":7874" : "") + "/nxt");
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(Nxt.connectTimeout);
            connection.setReadTimeout(Nxt.readTimeout);
            final CountingOutputStream cos = new CountingOutputStream(connection.getOutputStream());
            try (final Writer writer = new BufferedWriter(new OutputStreamWriter(cos, "UTF-8"))) {
                request.writeJSONString(writer);
            }
            this.updateUploadedVolume(cos.getCount());
            if (connection.getResponseCode() == 200) {
                if ((Nxt.communicationLoggingMask & 0x4) != 0x0) {
                    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    final byte[] buffer = new byte[65536];
                    try (final InputStream inputStream = connection.getInputStream()) {
                        int numberOfBytes;
                        while ((numberOfBytes = inputStream.read(buffer)) > 0) {
                            byteArrayOutputStream.write(buffer, 0, numberOfBytes);
                        }
                    }
                    final String responseValue = byteArrayOutputStream.toString("UTF-8");
                    log = log + " >>> " + responseValue;
                    showLog = true;
                    this.updateDownloadedVolume(responseValue.getBytes("UTF-8").length);
                    response = (JSONObject)JSONValue.parse(responseValue);
                }
                else {
                    final CountingInputStream cis = new CountingInputStream(connection.getInputStream());
                    try (final Reader reader = new BufferedReader(new InputStreamReader(cis, "UTF-8"))) {
                        response = (JSONObject)JSONValue.parse(reader);
                    }
                    this.updateDownloadedVolume(cis.getCount());
                }
            }
            else {
                if ((Nxt.communicationLoggingMask & 0x2) != 0x0) {
                    log = log + " >>> Peer responded with HTTP " + connection.getResponseCode() + " code!";
                    showLog = true;
                }
                this.disconnect();
                response = null;
            }
        }
        catch (RuntimeException | IOException e) {
            if (!(e instanceof ConnectException) && !(e instanceof UnknownHostException) && !(e instanceof NoRouteToHostException) && !(e instanceof SocketTimeoutException) && !(e instanceof SocketException)) {
                Nxt.logDebugMessage("Error sending JSON request", e);
            }
            if ((Nxt.communicationLoggingMask & 0x1) != 0x0) {
                log = log + " >>> " + e.toString();
                showLog = true;
            }
            if (this.state == 0) {
                this.blacklist();
            }
            else {
                this.disconnect();
            }
            response = null;
        }
        if (showLog) {
            Nxt.logMessage(log + "\n");
        }
        if (connection != null) {
            connection.disconnect();
        }
        return response;
    }
    
    void setState(final int state) {
        if (this.state == 0 && state != 0) {
            final JSONObject response = new JSONObject();
            response.put((Object)"response", (Object)"processNewData");
            if (this.announcedAddress.length() > 0) {
                final JSONArray removedKnownPeers = new JSONArray();
                final JSONObject removedKnownPeer = new JSONObject();
                removedKnownPeer.put((Object)"index", (Object)this.index);
                removedKnownPeers.add((Object)removedKnownPeer);
                response.put((Object)"removedKnownPeers", (Object)removedKnownPeers);
            }
            final JSONArray addedActivePeers = new JSONArray();
            final JSONObject addedActivePeer = new JSONObject();
            addedActivePeer.put((Object)"index", (Object)this.index);
            if (state == 2) {
                addedActivePeer.put((Object)"disconnected", (Object)true);
            }
            for (final Map.Entry<String, Peer> peerEntry : Nxt.peers.entrySet()) {
                if (peerEntry.getValue() == this) {
                    addedActivePeer.put((Object)"address", (Object)((peerEntry.getKey().length() > 30) ? (peerEntry.getKey().substring(0, 30) + "...") : peerEntry.getKey()));
                    break;
                }
            }
            addedActivePeer.put((Object)"announcedAddress", (Object)((this.announcedAddress.length() > 30) ? (this.announcedAddress.substring(0, 30) + "...") : this.announcedAddress));
            addedActivePeer.put((Object)"weight", (Object)this.getWeight());
            addedActivePeer.put((Object)"downloaded", (Object)this.downloadedVolume);
            addedActivePeer.put((Object)"uploaded", (Object)this.uploadedVolume);
            addedActivePeer.put((Object)"software", (Object)this.getSoftware());
            for (final String wellKnownPeer : Nxt.wellKnownPeers) {
                if (this.announcedAddress.equals(wellKnownPeer)) {
                    addedActivePeer.put((Object)"wellKnown", (Object)true);
                    break;
                }
            }
            addedActivePeers.add((Object)addedActivePeer);
            response.put((Object)"addedActivePeers", (Object)addedActivePeers);
            for (final User user : Nxt.users.values()) {
                user.send(response);
            }
        }
        else if (this.state != 0 && state != 0) {
            final JSONObject response = new JSONObject();
            response.put((Object)"response", (Object)"processNewData");
            final JSONArray changedActivePeers = new JSONArray();
            final JSONObject changedActivePeer = new JSONObject();
            changedActivePeer.put((Object)"index", (Object)this.index);
            changedActivePeer.put((Object)((state == 1) ? "connected" : "disconnected"), (Object)true);
            changedActivePeers.add((Object)changedActivePeer);
            response.put((Object)"changedActivePeers", (Object)changedActivePeers);
            for (final User user : Nxt.users.values()) {
                user.send(response);
            }
        }
        this.state = state;
    }
    
    void updateDownloadedVolume(final long volume) {
        this.downloadedVolume += volume;
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        final JSONArray changedActivePeers = new JSONArray();
        final JSONObject changedActivePeer = new JSONObject();
        changedActivePeer.put((Object)"index", (Object)this.index);
        changedActivePeer.put((Object)"downloaded", (Object)this.downloadedVolume);
        changedActivePeers.add((Object)changedActivePeer);
        response.put((Object)"changedActivePeers", (Object)changedActivePeers);
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
    }
    
    void updateUploadedVolume(final long volume) {
        this.uploadedVolume += volume;
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        final JSONArray changedActivePeers = new JSONArray();
        final JSONObject changedActivePeer = new JSONObject();
        changedActivePeer.put((Object)"index", (Object)this.index);
        changedActivePeer.put((Object)"uploaded", (Object)this.uploadedVolume);
        changedActivePeers.add((Object)changedActivePeer);
        response.put((Object)"changedActivePeers", (Object)changedActivePeers);
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
    }
    
    void updateWeight() {
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        final JSONArray changedActivePeers = new JSONArray();
        final JSONObject changedActivePeer = new JSONObject();
        changedActivePeer.put((Object)"index", (Object)this.index);
        changedActivePeer.put((Object)"weight", (Object)this.getWeight());
        changedActivePeers.add((Object)changedActivePeer);
        response.put((Object)"changedActivePeers", (Object)changedActivePeers);
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
    }
}
