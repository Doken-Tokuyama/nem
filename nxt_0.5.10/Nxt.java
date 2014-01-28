import java.math.*;
import java.util.concurrent.atomic.*;
import java.text.*;
import java.security.*;
import java.nio.*;
import javax.servlet.http.*;
import java.util.*;
import java.lang.ref.*;
import org.json.simple.*;
import java.net.*;
import java.util.concurrent.*;
import javax.servlet.*;
import java.io.*;

public class Nxt extends HttpServlet
{
    static final String VERSION = "0.5.10";
    static final long GENESIS_BLOCK_ID = 2680262203532249785L;
    static final long CREATOR_ID = 1739068987193023818L;
    static final byte[] CREATOR_PUBLIC_KEY;
    static final int BLOCK_HEADER_LENGTH = 224;
    static final int MAX_NUMBER_OF_TRANSACTIONS = 255;
    static final int MAX_PAYLOAD_LENGTH = 32640;
    static final int MAX_ARBITRARY_MESSAGE_LENGTH = 1000;
    static final int ALIAS_SYSTEM_BLOCK = 22000;
    static final int TRANSPARENT_FORGING_BLOCK = 30000;
    static final int ARBITRARY_MESSAGES_BLOCK = 40000;
    static final int TRANSPARENT_FORGING_BLOCK_2 = 47000;
    static final int TRANSPARENT_FORGING_BLOCK_3 = 51000;
    static final byte[] CHECKSUM_TRANSPARENT_FORGING;
    static final long MAX_BALANCE = 1000000000L;
    static final long initialBaseTarget = 153722867L;
    static final long maxBaseTarget = 153722867000000000L;
    static final long MAX_ASSET_QUANTITY = 1000000000L;
    static final BigInteger two64;
    static long epochBeginning;
    static final String alphabet = "0123456789abcdefghijklmnopqrstuvwxyz";
    static String myPlatform;
    static String myScheme;
    static String myAddress;
    static String myHallmark;
    static int myPort;
    static boolean shareMyAddress;
    static Set<String> allowedUserHosts;
    static Set<String> allowedBotHosts;
    static int blacklistingPeriod;
    static final int LOGGING_MASK_EXCEPTIONS = 1;
    static final int LOGGING_MASK_NON200_RESPONSES = 2;
    static final int LOGGING_MASK_200_RESPONSES = 4;
    static int communicationLoggingMask;
    static final AtomicInteger transactionCounter;
    static final ConcurrentMap<Long, Transaction> transactions;
    static final ConcurrentMap<Long, Transaction> unconfirmedTransactions;
    static final ConcurrentMap<Long, Transaction> doubleSpendingTransactions;
    static final ConcurrentMap<Long, Transaction> nonBroadcastedTransactions;
    static Set<String> wellKnownPeers;
    static int maxNumberOfConnectedPublicPeers;
    static int connectTimeout;
    static int readTimeout;
    static boolean enableHallmarkProtection;
    static int pushThreshold;
    static int pullThreshold;
    static int sendToPeersLimit;
    static final AtomicInteger peerCounter;
    static final ConcurrentMap<String, Peer> peers;
    static final Object blocksAndTransactionsLock;
    static final AtomicInteger blockCounter;
    static final ConcurrentMap<Long, Block> blocks;
    static final AtomicReference<Block> lastBlock;
    static volatile Peer lastBlockchainFeeder;
    static final ConcurrentMap<Long, Account> accounts;
    static final ConcurrentMap<String, Alias> aliases;
    static final ConcurrentMap<Long, Alias> aliasIdToAliasMappings;
    static final ConcurrentMap<Long, Asset> assets;
    static final ConcurrentMap<String, Long> assetNameToIdMappings;
    static final ConcurrentMap<Long, AskOrder> askOrders;
    static final ConcurrentMap<Long, BidOrder> bidOrders;
    static final ConcurrentMap<Long, TreeSet<AskOrder>> sortedAskOrders;
    static final ConcurrentMap<Long, TreeSet<BidOrder>> sortedBidOrders;
    static final ConcurrentMap<String, User> users;
    static final ScheduledExecutorService scheduledThreadPool;
    static final ExecutorService sendToPeersService;
    static final ThreadLocal<SimpleDateFormat> logDateFormat;
    static final boolean debug;
    static final boolean enableStackTraces;
    
    static int getEpochTime(final long time) {
        return (int)((time - Nxt.epochBeginning + 500L) / 1000L);
    }
    
    static void logMessage(final String message) {
        System.out.println(Nxt.logDateFormat.get().format(new Date()) + message);
    }
    
    static void logMessage(final String message, final Exception e) {
        if (Nxt.enableStackTraces) {
            logMessage(message);
            e.printStackTrace();
        }
        else {
            logMessage(message + ":\n" + e.toString());
        }
    }
    
    static void logDebugMessage(final String message) {
        if (Nxt.debug) {
            logMessage("DEBUG: " + message);
        }
    }
    
    static void logDebugMessage(final String message, final Exception e) {
        if (Nxt.debug) {
            if (Nxt.enableStackTraces) {
                logMessage("DEBUG: " + message);
                e.printStackTrace();
            }
            else {
                logMessage("DEBUG: " + message + ":\n" + e.toString());
            }
        }
    }
    
    static byte[] convert(final String string) {
        final byte[] bytes = new byte[string.length() / 2];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte)Integer.parseInt(string.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
    
    static String convert(final byte[] bytes) {
        final StringBuilder string = new StringBuilder();
        for (final byte b : bytes) {
            final int number;
            string.append("0123456789abcdefghijklmnopqrstuvwxyz".charAt((number = (b & 0xFF)) >> 4)).append("0123456789abcdefghijklmnopqrstuvwxyz".charAt(number & 0xF));
        }
        return string.toString();
    }
    
    static String convert(final long objectId) {
        BigInteger id = BigInteger.valueOf(objectId);
        if (objectId < 0L) {
            id = id.add(Nxt.two64);
        }
        return id.toString();
    }
    
    static long parseUnsignedLong(final String number) {
        if (number == null) {
            throw new IllegalArgumentException("trying to parse null");
        }
        final BigInteger bigInt = new BigInteger(number.trim());
        if (bigInt.signum() < 0 || bigInt.compareTo(Nxt.two64) != -1) {
            throw new IllegalArgumentException("overflow: " + number);
        }
        return bigInt.longValue();
    }
    
    static MessageDigest getMessageDigest(final String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException e) {
            logMessage("Missing message digest algorithm: " + algorithm);
            System.exit(1);
            return null;
        }
    }
    
    static void matchOrders(final long assetId) {
        final TreeSet<AskOrder> sortedAskOrders = Nxt.sortedAskOrders.get(assetId);
        final TreeSet<BidOrder> sortedBidOrders = Nxt.sortedBidOrders.get(assetId);
        while (!sortedAskOrders.isEmpty() && !sortedBidOrders.isEmpty()) {
            final AskOrder askOrder = sortedAskOrders.first();
            final BidOrder bidOrder = sortedBidOrders.first();
            if (askOrder.price > bidOrder.price) {
                break;
            }
            final int quantity = (askOrder.quantity < bidOrder.quantity) ? askOrder.quantity : bidOrder.quantity;
            final long price = (askOrder.height < bidOrder.height || (askOrder.height == bidOrder.height && askOrder.id < bidOrder.id)) ? askOrder.price : bidOrder.price;
            final AskOrder askOrder2 = askOrder;
            if ((askOrder2.quantity -= quantity) == 0) {
                Nxt.askOrders.remove(askOrder.id);
                sortedAskOrders.remove(askOrder);
            }
            askOrder.account.addToBalanceAndUnconfirmedBalance(quantity * price);
            final BidOrder bidOrder2 = bidOrder;
            if ((bidOrder2.quantity -= quantity) == 0) {
                Nxt.bidOrders.remove(bidOrder.id);
                sortedBidOrders.remove(bidOrder);
            }
            bidOrder.account.addToAssetAndUnconfirmedAssetBalance(assetId, quantity);
        }
    }
    
    public void init(final ServletConfig servletConfig) throws ServletException {
        logMessage("NRS 0.5.10 starting...");
        if (Nxt.debug) {
            logMessage("DEBUG logging enabled");
        }
        if (Nxt.enableStackTraces) {
            logMessage("logging of exception stack traces enabled");
        }
        try {
            final Calendar calendar = Calendar.getInstance();
            calendar.set(15, 0);
            calendar.set(1, 2013);
            calendar.set(2, 10);
            calendar.set(5, 24);
            calendar.set(11, 12);
            calendar.set(12, 0);
            calendar.set(13, 0);
            calendar.set(14, 0);
            Nxt.epochBeginning = calendar.getTimeInMillis();
            Nxt.myPlatform = servletConfig.getInitParameter("myPlatform");
            logMessage("\"myPlatform\" = \"" + Nxt.myPlatform + "\"");
            if (Nxt.myPlatform == null) {
                Nxt.myPlatform = "PC";
            }
            else {
                Nxt.myPlatform = Nxt.myPlatform.trim();
            }
            Nxt.myScheme = servletConfig.getInitParameter("myScheme");
            logMessage("\"myScheme\" = \"" + Nxt.myScheme + "\"");
            if (Nxt.myScheme == null) {
                Nxt.myScheme = "http";
            }
            else {
                Nxt.myScheme = Nxt.myScheme.trim();
            }
            final String myPort = servletConfig.getInitParameter("myPort");
            logMessage("\"myPort\" = \"" + myPort + "\"");
            try {
                Nxt.myPort = Integer.parseInt(myPort);
            }
            catch (NumberFormatException e2) {
                Nxt.myPort = (Nxt.myScheme.equals("https") ? 7875 : 7874);
                logMessage("Invalid value for myPort " + myPort + ", using default " + Nxt.myPort);
            }
            Nxt.myAddress = servletConfig.getInitParameter("myAddress");
            logMessage("\"myAddress\" = \"" + Nxt.myAddress + "\"");
            if (Nxt.myAddress != null) {
                Nxt.myAddress = Nxt.myAddress.trim();
            }
            final String shareMyAddress = servletConfig.getInitParameter("shareMyAddress");
            logMessage("\"shareMyAddress\" = \"" + shareMyAddress + "\"");
            Nxt.shareMyAddress = Boolean.parseBoolean(shareMyAddress);
            Nxt.myHallmark = servletConfig.getInitParameter("myHallmark");
            logMessage("\"myHallmark\" = \"" + Nxt.myHallmark + "\"");
            if (Nxt.myHallmark != null) {
                Nxt.myHallmark = Nxt.myHallmark.trim();
                try {
                    convert(Nxt.myHallmark);
                }
                catch (NumberFormatException e3) {
                    logMessage("Your hallmark is invalid: " + Nxt.myHallmark);
                    System.exit(1);
                }
            }
            final String wellKnownPeers = servletConfig.getInitParameter("wellKnownPeers");
            logMessage("\"wellKnownPeers\" = \"" + wellKnownPeers + "\"");
            if (wellKnownPeers != null) {
                final Set<String> set = new HashSet<String>();
                for (String wellKnownPeer : wellKnownPeers.split(";")) {
                    wellKnownPeer = wellKnownPeer.trim();
                    if (wellKnownPeer.length() > 0) {
                        set.add(wellKnownPeer);
                        Peer.addPeer(wellKnownPeer, wellKnownPeer);
                    }
                }
                Nxt.wellKnownPeers = Collections.unmodifiableSet((Set<? extends String>)set);
            }
            else {
                Nxt.wellKnownPeers = Collections.emptySet();
                logMessage("No wellKnownPeers defined, it is unlikely to work");
            }
            final String maxNumberOfConnectedPublicPeers = servletConfig.getInitParameter("maxNumberOfConnectedPublicPeers");
            logMessage("\"maxNumberOfConnectedPublicPeers\" = \"" + maxNumberOfConnectedPublicPeers + "\"");
            try {
                Nxt.maxNumberOfConnectedPublicPeers = Integer.parseInt(maxNumberOfConnectedPublicPeers);
            }
            catch (NumberFormatException e4) {
                Nxt.maxNumberOfConnectedPublicPeers = 10;
                logMessage("Invalid value for maxNumberOfConnectedPublicPeers " + maxNumberOfConnectedPublicPeers + ", using default " + Nxt.maxNumberOfConnectedPublicPeers);
            }
            final String connectTimeout = servletConfig.getInitParameter("connectTimeout");
            logMessage("\"connectTimeout\" = \"" + connectTimeout + "\"");
            try {
                Nxt.connectTimeout = Integer.parseInt(connectTimeout);
            }
            catch (NumberFormatException e5) {
                Nxt.connectTimeout = 1000;
                logMessage("Invalid value for connectTimeout " + connectTimeout + ", using default " + Nxt.connectTimeout);
            }
            final String readTimeout = servletConfig.getInitParameter("readTimeout");
            logMessage("\"readTimeout\" = \"" + readTimeout + "\"");
            try {
                Nxt.readTimeout = Integer.parseInt(readTimeout);
            }
            catch (NumberFormatException e6) {
                Nxt.readTimeout = 1000;
                logMessage("Invalid value for readTimeout " + readTimeout + ", using default " + Nxt.readTimeout);
            }
            final String enableHallmarkProtection = servletConfig.getInitParameter("enableHallmarkProtection");
            logMessage("\"enableHallmarkProtection\" = \"" + enableHallmarkProtection + "\"");
            Nxt.enableHallmarkProtection = Boolean.parseBoolean(enableHallmarkProtection);
            final String pushThreshold = servletConfig.getInitParameter("pushThreshold");
            logMessage("\"pushThreshold\" = \"" + pushThreshold + "\"");
            try {
                Nxt.pushThreshold = Integer.parseInt(pushThreshold);
            }
            catch (NumberFormatException e7) {
                Nxt.pushThreshold = 0;
                logMessage("Invalid value for pushThreshold " + pushThreshold + ", using default " + Nxt.pushThreshold);
            }
            final String pullThreshold = servletConfig.getInitParameter("pullThreshold");
            logMessage("\"pullThreshold\" = \"" + pullThreshold + "\"");
            try {
                Nxt.pullThreshold = Integer.parseInt(pullThreshold);
            }
            catch (NumberFormatException e8) {
                Nxt.pullThreshold = 0;
                logMessage("Invalid value for pullThreshold " + pullThreshold + ", using default " + Nxt.pullThreshold);
            }
            final String allowedUserHosts = servletConfig.getInitParameter("allowedUserHosts");
            logMessage("\"allowedUserHosts\" = \"" + allowedUserHosts + "\"");
            if (allowedUserHosts != null && !allowedUserHosts.trim().equals("*")) {
                final Set<String> set2 = new HashSet<String>();
                for (String allowedUserHost : allowedUserHosts.split(";")) {
                    allowedUserHost = allowedUserHost.trim();
                    if (allowedUserHost.length() > 0) {
                        set2.add(allowedUserHost);
                    }
                }
                Nxt.allowedUserHosts = Collections.unmodifiableSet((Set<? extends String>)set2);
            }
            final String allowedBotHosts = servletConfig.getInitParameter("allowedBotHosts");
            logMessage("\"allowedBotHosts\" = \"" + allowedBotHosts + "\"");
            if (allowedBotHosts != null && !allowedBotHosts.trim().equals("*")) {
                final Set<String> set3 = new HashSet<String>();
                for (String allowedBotHost : allowedBotHosts.split(";")) {
                    allowedBotHost = allowedBotHost.trim();
                    if (allowedBotHost.length() > 0) {
                        set3.add(allowedBotHost);
                    }
                }
                Nxt.allowedBotHosts = Collections.unmodifiableSet((Set<? extends String>)set3);
            }
            final String blacklistingPeriod = servletConfig.getInitParameter("blacklistingPeriod");
            logMessage("\"blacklistingPeriod\" = \"" + blacklistingPeriod + "\"");
            try {
                Nxt.blacklistingPeriod = Integer.parseInt(blacklistingPeriod);
            }
            catch (NumberFormatException e9) {
                Nxt.blacklistingPeriod = 300000;
                logMessage("Invalid value for blacklistingPeriod " + blacklistingPeriod + ", using default " + Nxt.blacklistingPeriod);
            }
            final String communicationLoggingMask = servletConfig.getInitParameter("communicationLoggingMask");
            logMessage("\"communicationLoggingMask\" = \"" + communicationLoggingMask + "\"");
            try {
                Nxt.communicationLoggingMask = Integer.parseInt(communicationLoggingMask);
            }
            catch (NumberFormatException e10) {
                logMessage("Invalid value for communicationLogginMask " + communicationLoggingMask + ", using default 0");
            }
            final String sendToPeersLimit = servletConfig.getInitParameter("sendToPeersLimit");
            logMessage("\"sendToPeersLimit\" = \"" + sendToPeersLimit + "\"");
            try {
                Nxt.sendToPeersLimit = Integer.parseInt(sendToPeersLimit);
            }
            catch (NumberFormatException e11) {
                Nxt.sendToPeersLimit = 10;
                logMessage("Invalid value for sendToPeersLimit " + sendToPeersLimit + ", using default " + Nxt.sendToPeersLimit);
            }
            try {
                logMessage("Loading transactions...");
                Transaction.loadTransactions("transactions.nxt");
                logMessage("...Done");
            }
            catch (FileNotFoundException e12) {
                logMessage("transactions.nxt not found, starting from scratch");
                Nxt.transactions.clear();
                final long[] recipients = { new BigInteger("163918645372308887").longValue(), new BigInteger("620741658595224146").longValue(), new BigInteger("723492359641172834").longValue(), new BigInteger("818877006463198736").longValue(), new BigInteger("1264744488939798088").longValue(), new BigInteger("1600633904360147460").longValue(), new BigInteger("1796652256451468602").longValue(), new BigInteger("1814189588814307776").longValue(), new BigInteger("1965151371996418680").longValue(), new BigInteger("2175830371415049383").longValue(), new BigInteger("2401730748874927467").longValue(), new BigInteger("2584657662098653454").longValue(), new BigInteger("2694765945307858403").longValue(), new BigInteger("3143507805486077020").longValue(), new BigInteger("3684449848581573439").longValue(), new BigInteger("4071545868996394636").longValue(), new BigInteger("4277298711855908797").longValue(), new BigInteger("4454381633636789149").longValue(), new BigInteger("4747512364439223888").longValue(), new BigInteger("4777958973882919649").longValue(), new BigInteger("4803826772380379922").longValue(), new BigInteger("5095742298090230979").longValue(), new BigInteger("5271441507933314159").longValue(), new BigInteger("5430757907205901788").longValue(), new BigInteger("5491587494620055787").longValue(), new BigInteger("5622658764175897611").longValue(), new BigInteger("5982846390354787993").longValue(), new BigInteger("6290807999797358345").longValue(), new BigInteger("6785084810899231190").longValue(), new BigInteger("6878906112724074600").longValue(), new BigInteger("7017504655955743955").longValue(), new BigInteger("7085298282228890923").longValue(), new BigInteger("7446331133773682477").longValue(), new BigInteger("7542917420413518667").longValue(), new BigInteger("7549995577397145669").longValue(), new BigInteger("7577840883495855927").longValue(), new BigInteger("7579216551136708118").longValue(), new BigInteger("8278234497743900807").longValue(), new BigInteger("8517842408878875334").longValue(), new BigInteger("8870453786186409991").longValue(), new BigInteger("9037328626462718729").longValue(), new BigInteger("9161949457233564608").longValue(), new BigInteger("9230759115816986914").longValue(), new BigInteger("9306550122583806885").longValue(), new BigInteger("9433259657262176905").longValue(), new BigInteger("9988839211066715803").longValue(), new BigInteger("10105875265190846103").longValue(), new BigInteger("10339765764359265796").longValue(), new BigInteger("10738613957974090819").longValue(), new BigInteger("10890046632913063215").longValue(), new BigInteger("11494237785755831723").longValue(), new BigInteger("11541844302056663007").longValue(), new BigInteger("11706312660844961581").longValue(), new BigInteger("12101431510634235443").longValue(), new BigInteger("12186190861869148835").longValue(), new BigInteger("12558748907112364526").longValue(), new BigInteger("13138516747685979557").longValue(), new BigInteger("13330279748251018740").longValue(), new BigInteger("14274119416917666908").longValue(), new BigInteger("14557384677985343260").longValue(), new BigInteger("14748294830376619968").longValue(), new BigInteger("14839596582718854826").longValue(), new BigInteger("15190676494543480574").longValue(), new BigInteger("15253761794338766759").longValue(), new BigInteger("15558257163011348529").longValue(), new BigInteger("15874940801139996458").longValue(), new BigInteger("16516270647696160902").longValue(), new BigInteger("17156841960446798306").longValue(), new BigInteger("17228894143802851995").longValue(), new BigInteger("17240396975291969151").longValue(), new BigInteger("17491178046969559641").longValue(), new BigInteger("18345202375028346230").longValue(), new BigInteger("18388669820699395594").longValue() };
                final int[] amounts = { 36742, 1970092, 349130, 24880020, 2867856, 9975150, 2690963, 7648, 5486333, 34913026, 997515, 30922966, 6650, 44888, 2468850, 49875751, 49875751, 9476393, 49875751, 14887912, 528683, 583546, 7315, 19925363, 29856290, 5320, 4987575, 5985, 24912938, 49875751, 2724712, 1482474, 200999, 1476156, 498758, 987540, 16625250, 5264386, 15487585, 2684479, 14962725, 34913026, 5033128, 2916900, 49875751, 4962637, 170486123, 8644631, 22166945, 6668388, 233751, 4987575, 11083556, 1845403, 49876, 3491, 3491, 9476, 49876, 6151, 682633, 49875751, 482964, 4988, 49875751, 4988, 9144, 503745, 49875751, 52370, 29437998, 585375, 9975150 };
                final byte[][] signatures = { { 41, 115, -41, 7, 37, 21, -3, -41, 120, 119, 63, -101, 108, 48, -117, 1, -43, 32, 85, 95, 65, 42, 92, -22, 123, -36, 6, -99, -61, -53, 93, 7, 23, 8, -30, 65, 57, -127, -2, 42, -92, -104, 11, 72, -66, 108, 17, 113, 99, -117, -75, 123, 110, 107, 119, -25, 67, 64, 32, 117, 111, 54, 82, -14 }, { 118, 43, 84, -91, -110, -102, 100, -40, -33, -47, -13, -7, -88, 2, -42, -66, -38, -22, 105, -42, -69, 78, 51, -55, -48, 49, -89, 116, -96, -104, -114, 14, 94, 58, -115, -8, 111, -44, 76, -104, 54, -15, 126, 31, 6, -80, 65, 6, 124, 37, -73, 92, 4, 122, 122, -108, 1, -54, 31, -38, -117, -1, -52, -56 }, { 79, 100, -101, 107, -6, -61, 40, 32, -98, 32, 80, -59, -76, -23, -62, 38, 4, 105, -106, -105, -121, -85, 13, -98, -77, 126, -125, 103, 12, -41, 1, 2, 45, -62, -69, 102, 116, -61, 101, -14, -68, -31, 9, 110, 18, 2, 33, -98, -37, -128, 17, -19, 124, 125, -63, 92, -70, 96, 96, 125, 91, 8, -65, -12 }, { 58, -99, 14, -97, -75, -10, 110, -102, 119, -3, -2, -12, -82, -33, -27, 118, -19, 55, -109, 6, 110, -10, 108, 30, 94, -113, -5, -98, 19, 12, -125, 14, -77, 33, -128, -21, 36, -120, -12, -81, 64, 95, 67, -3, 100, 122, -47, 127, -92, 114, 68, 72, 2, -40, 80, 117, -17, -56, 103, 37, -119, 3, 22, 23 }, { 76, 22, 121, -4, -77, -127, 18, -102, 7, 94, -73, -96, 108, -11, 81, -18, -37, -85, -75, 86, -119, 94, 126, 95, 47, -36, -16, -50, -9, 95, 60, 15, 14, 93, -108, -83, -67, 29, 2, -53, 10, -118, -51, -46, 92, -23, -56, 60, 46, -90, -84, 126, 60, 78, 12, 53, 61, 121, -6, 77, 112, 60, 40, 63 }, { 64, 121, -73, 68, 4, -103, 81, 55, -41, -81, -63, 10, 117, -74, 54, -13, -85, 79, 21, 116, -25, -12, 21, 120, -36, -80, 53, -78, 103, 25, 55, 6, -75, 96, 80, -125, -11, -103, -20, -41, 121, -61, -40, 63, 24, -81, -125, 90, -12, -40, -52, -1, -114, 14, -44, -112, -80, 83, -63, 88, -107, -10, -114, -86 }, { -81, 126, -41, -34, 66, -114, -114, 114, 39, 32, -125, -19, -95, -50, -111, -51, -33, 51, 99, -127, 58, 50, -110, 44, 80, -94, -96, 68, -69, 34, 86, 3, -82, -69, 28, 20, -111, 69, 18, -41, -23, 27, -118, 20, 72, 21, -112, 53, -87, -81, -47, -101, 123, -80, 99, -15, 33, -120, -8, 82, 80, -8, -10, -45 }, { 92, 77, 53, -87, 26, 13, -121, -39, -62, -42, 47, 4, 7, 108, -15, 112, 103, 38, -50, -74, 60, 56, -63, 43, -116, 49, -106, 69, 118, 65, 17, 12, 31, 127, -94, 55, -117, -29, -117, 31, -95, -110, -2, 63, -73, -106, -88, -41, -19, 69, 60, -17, -16, 61, 32, -23, 88, -106, -96, 37, -96, 114, -19, -99 }, { 68, -26, 57, -56, -30, 108, 61, 24, 106, -56, -92, 99, -59, 107, 25, -110, -57, 80, 79, -92, -107, 90, 54, -73, -40, -39, 78, 109, -57, -62, -17, 6, -25, -29, 37, 90, -24, -27, -61, -69, 44, 121, 107, -72, -57, 108, 32, -69, -21, -41, 126, 91, 118, 11, -91, 50, -11, 116, 126, -96, -39, 110, 105, -52 }, { 48, 108, 123, 50, -50, -58, 33, 14, 59, 102, 17, -18, -119, 4, 10, -29, 36, -56, -31, 43, -71, -48, -14, 87, 119, -119, 40, 104, -44, -76, -24, 2, 48, -96, -7, 16, -119, -3, 108, 78, 125, 88, 61, -53, -3, -16, 20, -83, 74, 124, -47, -17, -15, -21, -23, -119, -47, 105, -4, 115, -20, 77, 57, 88 }, { 33, 101, 79, -35, 32, -119, 20, 120, 34, -80, -41, 90, -22, 93, -20, -45, 9, 24, -46, 80, -55, -9, -24, -78, -124, 27, -120, -36, -51, 59, -38, 7, 113, 125, 68, 109, 24, -121, 111, 37, -71, 100, -111, 78, -43, -14, -76, -44, 64, 103, 16, -28, -44, -103, 74, 81, -118, -74, 47, -77, -65, 8, 42, -100 }, { -63, -96, -95, -111, -85, -98, -85, 42, 87, 29, -62, -57, 57, 48, 9, -39, -110, 63, -103, -114, -48, -11, -92, 105, -26, -79, -11, 78, -118, 14, -39, 1, -115, 74, 70, -41, -119, -68, -39, -60, 64, 31, 25, -111, -16, -20, 61, -22, 17, -13, 57, -110, 24, 61, -104, 21, -72, -69, 56, 116, -117, 93, -1, -123 }, { -18, -70, 12, 112, -111, 10, 22, 31, -120, 26, 53, 14, 10, 69, 51, 45, -50, -127, -22, 95, 54, 17, -8, 54, -115, 36, -79, 12, -79, 82, 4, 1, 92, 59, 23, -13, -85, -87, -110, -58, 84, -31, -48, -105, -101, -92, -9, 28, -109, 77, -47, 100, -48, -83, 106, -102, 70, -95, 94, -1, -99, -15, 21, 99 }, { 109, 123, 54, 40, -120, 32, -118, 49, -52, 0, -103, 103, 101, -9, 32, 78, 124, -56, 88, -19, 101, -32, 70, 67, -41, 85, -103, 1, 1, -105, -51, 10, 4, 51, -26, -19, 39, -43, 63, -41, -101, 80, 106, -66, 125, 47, -117, -120, -93, -120, 99, -113, -17, 61, 102, -2, 72, 9, -124, 123, -128, 78, 43, 96 }, { -22, -63, 20, 65, 5, -89, -123, -61, 14, 34, 83, -113, 34, 85, 26, -21, 1, 16, 88, 55, -92, -111, 14, -31, -37, -67, -8, 85, 39, -112, -33, 8, 28, 16, 107, -29, 1, 3, 100, -53, 2, 81, 52, -94, -14, 36, -123, -82, -6, -118, 104, 75, -99, -82, -100, 7, 30, -66, 0, -59, 108, -54, 31, 20 }, { 0, 13, -74, 28, -54, -12, 45, 36, -24, 55, 43, -110, -72, 117, -110, -56, -72, 85, 79, -89, -92, 65, -67, -34, -24, 38, 67, 42, 84, -94, 91, 13, 100, 89, 20, -95, -76, 2, 116, 34, 67, 52, -80, -101, -22, -32, 51, 32, -76, 44, -93, 11, 42, -69, -12, 7, -52, -55, 122, -10, 48, 21, 92, 110 }, { -115, 19, 115, 28, -56, 118, 111, 26, 18, 123, 111, -96, -115, 120, 105, 62, -123, -124, 101, 51, 3, 18, -89, 127, 48, -27, 39, -78, -118, 5, -2, 6, -105, 17, 123, 26, 25, -62, -37, 49, 117, 3, 10, 97, -7, 54, 121, -90, -51, -49, 11, 104, -66, 11, -6, 57, 5, -64, -8, 59, 82, -126, 26, -113 }, { 16, -53, 94, 99, -46, -29, 64, -89, -59, 116, -21, 53, 14, -77, -71, 95, 22, -121, -51, 125, -14, -96, 95, 95, 32, 96, 79, 41, -39, -128, 79, 0, 5, 6, -115, 104, 103, 77, -92, 93, -109, 58, 96, 97, -22, 116, -62, 11, 30, -122, 14, 28, 69, 124, 63, -119, 19, 80, -36, -116, -76, -58, 36, 87 }, { 109, -82, 33, -119, 17, 109, -109, -16, 98, 108, 111, 5, 98, 1, -15, -32, 22, 46, -65, 117, -78, 119, 35, -35, -3, 41, 23, -97, 55, 69, 58, 9, 20, -113, -121, -13, -41, -48, 22, -73, -1, -44, -73, 3, -10, -122, 19, -103, 10, -26, -128, 62, 34, 55, 54, -43, 35, -30, 115, 64, -80, -20, -25, 67 }, { -16, -74, -116, -128, 52, 96, -75, 17, -22, 72, -43, 22, -95, -16, 32, -72, 98, 46, -4, 83, 34, -58, -108, 18, 17, -58, -123, 53, -108, 116, 18, 2, 7, -94, -126, -45, 72, -69, -65, -89, 64, 31, -78, 78, -115, 106, 67, 55, -123, 104, -128, 36, -23, -90, -14, -87, 78, 19, 18, -128, 39, 73, 35, 120 }, { 20, -30, 15, 111, -82, 39, -108, 57, -80, 98, -19, -27, 100, -18, 47, 77, -41, 95, 80, -113, -128, -88, -76, 115, 65, -53, 83, 115, 7, 2, -104, 3, 120, 115, 14, -116, 33, -15, -120, 22, -56, -8, -69, 5, -75, 94, 124, 12, -126, -48, 51, -105, 22, -66, -93, 16, -63, -74, 32, 114, -54, -3, -47, -126 }, { 56, -101, 55, -1, 64, 4, -64, 95, 31, -15, 72, 46, 67, -9, 68, -43, -55, 28, -63, -17, -16, 65, 11, -91, -91, 32, 88, 41, 60, 67, 105, 8, 58, 102, -79, -5, -113, -113, -67, 82, 50, -26, 116, -78, -103, 107, 102, 23, -74, -47, 115, -50, -35, 63, -80, -32, 72, 117, 47, 68, 86, -20, -35, 8 }, { 21, 27, 20, -59, 117, -102, -42, 22, -10, 121, 41, -59, 115, 15, -43, 54, -79, -62, -16, 58, 116, 15, 88, 108, 114, 67, 3, -30, -99, 78, 103, 11, 49, 63, -4, -110, -27, 41, 70, -57, -69, -18, 70, 30, -21, 66, -104, -27, 3, 53, 50, 100, -33, 54, -3, -78, 92, 85, -78, 54, 19, 32, 95, 9 }, { -93, 65, -64, -79, 82, 85, -34, -90, 122, -29, -40, 3, -80, -40, 32, 26, 102, -73, 17, 53, -93, -29, 122, 86, 107, -100, 50, 56, -28, 124, 90, 14, 93, -88, 97, 101, -85, -50, 46, -109, -88, -127, -112, 63, -89, 24, -34, -9, -116, -59, -87, -86, -12, 111, -111, 87, -87, -13, -73, -124, -47, 7, 1, 9 }, { 60, -99, -77, -20, 112, -75, -34, 100, -4, -96, 81, 71, -116, -62, 38, -68, 105, 7, -126, 21, -125, -25, -56, -11, -59, 95, 117, 108, 32, -38, -65, 13, 46, 65, -46, -89, 0, 120, 5, 23, 40, 110, 114, 79, 111, -70, 8, 16, -49, -52, -82, -18, 108, -43, 81, 96, 72, -65, 70, 7, -37, 58, 46, -14 }, { -95, -32, 85, 78, 74, -53, 93, -102, -26, -110, 86, 1, -93, -50, -23, -108, -37, 97, 19, 103, 94, -65, -127, -21, 60, 98, -51, -118, 82, -31, 27, 7, -112, -45, 79, 95, -20, 90, -4, -40, 117, 100, -6, 19, -47, 53, 53, 48, 105, 91, -70, -34, -5, -87, -57, -103, -112, -108, -40, 87, -25, 13, -76, -116 }, { 44, -122, -70, 125, -60, -32, 38, 69, -77, -103, 49, -124, -4, 75, -41, -84, 68, 74, 118, 15, -13, 115, 117, -78, 42, 89, 0, -20, -12, -58, -97, 10, -48, 95, 81, 101, 23, -67, -23, 74, -79, 21, 97, 123, 103, 101, -50, -115, 116, 112, 51, 50, -124, 27, 76, 40, 74, 10, 65, -49, 102, 95, 5, 35 }, { -6, 57, 71, 5, -61, -100, -21, -9, 47, -60, 59, 108, -75, 105, 56, 41, -119, 31, 37, 27, -86, 120, -125, -108, 121, 104, -21, -70, -57, -104, 2, 11, 118, 104, 68, 6, 7, -90, -70, -61, -16, 77, -8, 88, 31, -26, 35, -44, 8, 50, 51, -88, -62, -103, 54, -41, -2, 117, 98, -34, 49, -123, 83, -58 }, { 54, 21, -36, 126, -50, -72, 82, -5, -122, -116, 72, -19, -18, -68, -71, -27, 97, -22, 53, -94, 47, -6, 15, -92, -55, 127, 13, 13, -69, 81, -82, 8, -50, 10, 84, 110, -87, -44, 61, -78, -65, 84, -32, 48, -8, -105, 35, 116, -68, -116, -6, 75, -77, 120, -95, 74, 73, 105, 39, -87, 98, -53, 47, 10 }, { -113, 116, 37, -1, 95, -89, -93, 113, 36, -70, -57, -99, 94, 52, -81, -118, 98, 58, -36, 73, 82, -67, -80, 46, 83, -127, -8, 73, 66, -27, 43, 7, 108, 32, 73, 1, -56, -108, 41, -98, -15, 49, 1, 107, 65, 44, -68, 126, -28, -53, 120, -114, 126, -79, -14, -105, -33, 53, 5, -119, 67, 52, 35, -29 }, { 98, 23, 23, 83, 78, -89, 13, 55, -83, 97, -30, -67, 99, 24, 47, -4, -117, -34, -79, -97, 95, 74, 4, 21, 66, -26, 15, 80, 60, -25, -118, 14, 36, -55, -41, -124, 90, -1, 84, 52, 31, 88, 83, 121, -47, -59, -10, 17, 51, -83, 23, 108, 19, 104, 32, 29, -66, 24, 21, 110, 104, -71, -23, -103 }, { 12, -23, 60, 35, 6, -52, -67, 96, 15, -128, -47, -15, 40, 3, 54, -81, 3, 94, 3, -98, -94, -13, -74, -101, 40, -92, 90, -64, -98, 68, -25, 2, -62, -43, 112, 32, -78, -123, 26, -80, 126, 120, -88, -92, 126, -128, 73, -43, 87, -119, 81, 111, 95, -56, -128, -14, 51, -40, -42, 102, 46, 106, 6, 6 }, { -38, -120, -11, -114, -7, -105, -98, 74, 114, 49, 64, -100, 4, 40, 110, -21, 25, 6, -92, -40, -61, 48, 94, -116, -71, -87, 75, -31, 13, -119, 1, 5, 33, -69, -16, -125, -79, -46, -36, 3, 40, 1, -88, -118, -107, 95, -23, -107, -49, 44, -39, 2, 108, -23, 39, 50, -51, -59, -4, -42, -10, 60, 10, -103 }, { 67, -53, 55, -32, -117, 3, 94, 52, -115, -127, -109, 116, -121, -27, -115, -23, 98, 90, -2, 48, -54, -76, 108, -56, 99, 30, -35, -18, -59, 25, -122, 3, 43, -13, -109, 34, -10, 123, 117, 113, -112, -85, -119, -62, -78, -114, -96, 101, 72, -98, 28, 89, -98, -121, 20, 115, 89, -20, 94, -55, 124, 27, -76, 94 }, { 15, -101, 98, -21, 8, 5, -114, -64, 74, 123, 99, 28, 125, -33, 22, 23, -2, -56, 13, 91, 27, -105, -113, 19, 60, -7, -67, 107, 70, 103, -107, 13, -38, -108, -77, -29, 2, 9, -12, 21, 12, 65, 108, -16, 69, 77, 96, -54, 55, -78, -7, 41, -48, 124, -12, 64, 113, -45, -21, -119, -113, 88, -116, 113 }, { -17, 77, 10, 84, -57, -12, 101, 21, -91, 92, 17, -32, -26, 77, 70, 46, 81, -55, 40, 44, 118, -35, -97, 47, 5, 125, 41, -127, -72, 66, -18, 2, 115, -13, -74, 126, 86, 80, 11, -122, -29, -68, 113, 54, -117, 107, -75, -107, -54, 72, -44, 98, -111, -33, -56, -40, 93, -47, 84, -43, -45, 86, 65, -84 }, { -126, 60, -56, 121, 31, -124, -109, 100, -118, -29, 106, 94, 5, 27, 13, -79, 91, -111, -38, -42, 18, 61, -100, 118, -18, -4, -60, 121, 46, -22, 6, 4, -37, -20, 124, -43, 51, -57, -49, -44, -24, -38, 81, 60, -14, -97, -109, -11, -5, -85, 75, -17, -124, -96, -53, 52, 64, 100, -118, -120, 6, 60, 76, -110 }, { -12, -40, 115, -41, 68, 85, 20, 91, -44, -5, 73, -105, -81, 32, 116, 32, -28, 69, 88, -54, 29, -53, -51, -83, 54, 93, -102, -102, -23, 7, 110, 15, 34, 122, 84, 52, -121, 37, -103, -91, 37, -77, -101, 64, -18, 63, -27, -75, -112, -11, 1, -69, -25, -123, -99, -31, 116, 11, 4, -42, -124, 98, -2, 53 }, { -128, -69, -16, -33, -8, -112, 39, -57, 113, -76, -29, -37, 4, 121, -63, 12, -54, -66, -121, 13, -4, -44, 50, 27, 103, 101, 44, -115, 12, -4, -8, 10, 53, 108, 90, -47, 46, -113, 5, -3, -111, 8, -66, -73, 57, 72, 90, -33, 47, 99, 50, -55, 53, -4, 96, 87, 57, 26, 53, -45, -83, 39, -17, 45 }, { -121, 125, 60, -9, -79, -128, -19, 113, 54, 77, -23, -89, 105, -5, 47, 114, -120, -88, 31, 25, -96, -75, -6, 76, 9, -83, 75, -109, -126, -47, -6, 2, -59, 64, 3, 74, 100, 110, -96, 66, -3, 10, -124, -6, 8, 50, 109, 14, -109, 79, 73, 77, 67, 63, -50, 10, 86, -63, -125, -86, 35, -26, 7, 83 }, { 36, 31, -77, 126, 106, 97, 63, 81, -37, -126, 69, -127, -22, -69, 104, -111, 93, -49, 77, -3, -38, -112, 47, -55, -23, -68, -8, 78, -127, -28, -59, 10, 22, -61, -127, -13, -72, -14, -87, 14, 61, 76, -89, 81, -97, -97, -105, 94, -93, -9, -3, -104, -83, 59, 104, 121, -30, 106, -2, 62, -51, -72, -63, 55 }, { 81, -88, -8, -96, -31, 118, -23, -38, -94, 80, 35, -20, -93, -102, 124, 93, 0, 15, 36, -127, -41, -19, 6, -124, 94, -49, 44, 26, -69, 43, -58, 9, -18, -3, -2, 60, -122, -30, -47, 124, 71, 47, -74, -68, 4, -101, -16, 77, -120, -15, 45, -12, 68, -77, -74, 63, -113, 44, -71, 56, 122, -59, 53, -44 }, { 122, 30, 27, -79, 32, 115, -104, -28, -53, 109, 120, 121, -115, -65, -87, 101, 23, 10, 122, 101, 29, 32, 56, 63, -23, -48, -51, 51, 16, -124, -41, 6, -71, 49, -20, 26, -57, 65, 49, 45, 7, 49, -126, 54, -122, -43, 1, -5, 111, 117, 104, 117, 126, 114, -77, 66, -127, -50, 69, 14, 70, 73, 60, 112 }, { 104, -117, 105, -118, 35, 16, -16, 105, 27, -87, -43, -59, -13, -23, 5, 8, -112, -28, 18, -1, 48, 94, -82, 55, 32, 16, 59, -117, 108, -89, 101, 9, -35, 58, 70, 62, 65, 91, 14, -43, -104, 97, 1, -72, 16, -24, 79, 79, -85, -51, -79, -55, -128, 23, 109, -95, 17, 92, -38, 109, 65, -50, 46, -114 }, { 44, -3, 102, -60, -85, 66, 121, -119, 9, 82, -47, -117, 67, -28, 108, 57, -47, -52, -24, -82, 65, -13, 85, 107, -21, 16, -24, -85, 102, -92, 73, 5, 7, 21, 41, 47, -118, 72, 43, 51, -5, -64, 100, -34, -25, 53, -45, -115, 30, -72, -114, 126, 66, 60, -24, -67, 44, 48, 22, 117, -10, -33, -89, -108 }, { -7, 71, -93, -66, 3, 30, -124, -116, -48, -76, -7, -62, 125, -122, -60, -104, -30, 71, 36, -110, 34, -126, 31, 10, 108, 102, -53, 56, 104, -56, -48, 12, 25, 21, 19, -90, 45, -122, -73, -112, 97, 96, 115, 71, 127, -7, -46, 84, -24, 102, -104, -96, 28, 8, 37, -84, -13, -65, -6, 61, -85, -117, -30, 70 }, { -112, 39, -39, -24, 127, -115, 68, -1, -111, -43, 101, 20, -12, 39, -70, 67, -50, 68, 105, 69, -91, -106, 91, 4, -52, 75, 64, -121, 46, -117, 31, 10, -125, 77, 51, -3, -93, 58, 79, 121, 126, -29, 56, -101, 1, -28, 49, 16, -80, 92, -62, 83, 33, 17, 106, 89, -9, 60, 79, 38, -74, -48, 119, 24 }, { 105, -118, 34, 52, 111, 30, 38, -73, 125, -116, 90, 69, 2, 126, -34, -25, -41, -67, -23, -105, -12, -75, 10, 69, -51, -95, -101, 92, -80, -73, -120, 2, 71, 46, 11, -85, -18, 125, 81, 117, 33, -89, -42, 118, 51, 60, 89, 110, 97, -118, -111, -36, 75, 112, -4, -8, -36, -49, -55, 35, 92, 70, -37, 36 }, { 71, 4, -113, 13, -48, 29, -56, 82, 115, -38, -20, -79, -8, 126, -111, 5, -12, -56, -107, 98, 111, 19, 127, -10, -42, 24, -38, -123, 59, 51, -64, 3, 47, -1, -83, -127, -58, 86, 33, -76, 5, 71, -80, -50, -62, 116, 75, 20, -126, 23, -31, -21, 24, -83, -19, 114, -17, 1, 110, -70, -119, 126, 82, -83 }, { -77, -69, -45, -78, -78, 69, 35, 85, 84, 25, -66, -25, 53, -38, -2, 125, -38, 103, 88, 31, -9, -43, 15, -93, 69, -22, -13, -20, 73, 3, -100, 7, 26, -18, 123, -14, -78, 113, 79, -57, -109, -118, 105, -104, 75, -88, -24, -109, 73, -126, 9, 55, 98, -120, 93, 114, 74, 0, -86, -68, 47, 29, 75, 67 }, { -104, 11, -85, 16, -124, -91, 66, -91, 18, -67, -122, -57, -114, 88, 79, 11, -60, -119, 89, 64, 57, 120, -11, 8, 52, -18, -67, -127, 26, -19, -69, 2, -82, -56, 11, -90, -104, 110, -10, -68, 87, 21, 28, 87, -5, -74, -21, -84, 120, 70, -17, 102, 72, -116, -69, 108, -86, -79, -74, 115, -78, -67, 6, 45 }, { -6, -101, -17, 38, -25, -7, -93, 112, 13, -33, 121, 71, -79, -122, -95, 22, 47, -51, 16, 84, 55, -39, -26, 37, -36, -18, 11, 119, 106, -57, 42, 8, -1, 23, 7, -63, -9, -50, 30, 35, -125, 83, 9, -60, -94, -15, -76, 120, 18, -103, -70, 95, 26, 48, -103, -95, 10, 113, 66, 54, -96, -4, 37, 111 }, { -124, -53, 43, -59, -73, 99, 71, -36, -31, 61, -25, -14, -71, 48, 17, 10, -26, -21, -22, 104, 64, -128, 27, -40, 111, -70, -90, 91, -81, -88, -10, 11, -62, 127, -124, -2, -67, -69, 65, 73, 40, 82, 112, -112, 100, -26, 30, 86, 30, 1, -105, 45, 103, -47, -124, 58, 105, 24, 20, 108, -101, 84, -34, 80 }, { 28, -1, 84, 111, 43, 109, 57, -23, 52, -95, 110, -50, 77, 15, 80, 85, 125, -117, -10, 8, 59, -58, 18, 97, -58, 45, 92, -3, 56, 24, -117, 9, -73, -9, 48, -99, 50, -24, -3, -41, -43, 48, -77, -8, -89, -42, 126, 73, 28, -65, -108, 54, 6, 34, 32, 2, -73, -123, -106, -52, -73, -106, -112, 109 }, { 73, -76, -7, 49, 67, -34, 124, 80, 111, -91, -22, -121, -74, 42, -4, -18, 84, -3, 38, 126, 31, 54, -120, 65, -122, -14, -38, -80, -124, 90, 37, 1, 51, 123, 69, 48, 109, -112, -63, 27, 67, -127, 29, 79, -26, 99, -24, -100, 51, 103, -105, 13, 85, 74, 12, -37, 43, 80, -113, 6, 70, -107, -5, -80 }, { 110, -54, 109, 21, -124, 98, 90, -26, 69, -44, 17, 117, 78, -91, -7, -18, -81, -43, 20, 80, 48, -109, 117, 125, -67, 19, -15, 69, -28, 47, 15, 4, 34, -54, 51, -128, 18, 61, -77, -122, 100, -58, -118, -36, 5, 32, 43, 15, 60, -55, 120, 123, -77, -76, -121, 77, 93, 16, -73, 54, 46, -83, -39, 125 }, { 115, -15, -42, 111, -124, 52, 29, -124, -10, -23, 41, -128, 65, -60, -121, 6, -42, 14, 98, -80, 80, -46, -38, 64, 16, 84, -50, 47, -97, 11, -88, 12, 68, -127, -92, 87, -22, 54, -49, 33, -4, -68, 21, -7, -45, 84, 107, 57, 8, -106, 0, -87, -104, 93, -43, -98, -92, -72, 110, -14, -66, 119, 14, -68 }, { -19, 7, 7, 66, -94, 18, 36, 8, -58, -31, 21, -113, -124, -5, -12, 105, 40, -62, 57, -56, 25, 117, 49, 17, -33, 49, 105, 113, -26, 78, 97, 2, -22, -84, 49, 67, -6, 33, 89, 28, 30, 12, -3, -23, -45, 7, -4, -39, -20, 25, -91, 55, 53, 21, -94, 17, -54, 109, 125, 124, 122, 117, -125, 60 }, { -28, -104, -46, -22, 71, -79, 100, 48, -90, -57, -30, -23, -24, 1, 2, -31, 85, -6, -113, -116, 105, -31, -109, 106, 1, 78, -3, 103, -6, 100, -44, 15, -100, 97, 59, -42, 22, 83, 113, -118, 112, -57, 80, -45, -86, 72, 77, -26, -106, 50, 28, -24, 41, 22, -73, 108, 18, -93, 30, 8, -11, -16, 124, 106 }, { 16, -119, -109, 115, 67, 36, 28, 74, 101, -58, -82, 91, 4, -97, 111, -77, -37, -125, 126, 3, 10, -99, -115, 91, -66, -83, -81, 10, 7, 92, 26, 6, -45, 66, -26, 118, -77, 13, -91, 20, -18, -33, -103, 43, 75, -100, -5, -64, 117, 30, 5, -100, -90, 13, 18, -52, 26, 24, -10, 24, -31, 53, 88, 112 }, { 7, -90, 46, 109, -42, 108, -84, 124, -28, -63, 34, -19, -76, 88, -121, 23, 54, -73, -15, -52, 84, -119, 64, 20, 92, -91, -58, -121, -117, -90, -102, 1, 49, 21, 3, -85, -3, 38, 117, 73, -38, -71, -37, 40, -2, -50, -47, -46, 75, -105, 125, 126, -13, 68, 50, -81, -43, -93, 85, -79, 52, 98, 118, 50 }, { -104, 65, -61, 12, 68, 106, 37, -64, 40, -114, 61, 73, 74, 61, -113, -79, 57, 47, -57, -21, -68, -62, 23, -18, 93, -7, -55, -88, -106, 104, -126, 5, 53, 97, 100, -67, -112, -88, 41, 24, 95, 15, 25, -67, 79, -69, 53, 21, -128, -101, 73, 17, 7, -98, 5, -2, 33, -113, 99, -72, 125, 7, 18, -105 }, { -17, 28, 79, 34, 110, 86, 43, 27, -114, -112, -126, -98, -121, 126, -21, 111, 58, -114, -123, 75, 117, -116, 7, 107, 90, 80, -75, -121, 116, -11, -76, 0, -117, -52, 76, -116, 115, -117, 61, -7, 55, -34, 38, 101, 86, -19, -36, -92, -94, 61, 88, -128, -121, -103, 84, 19, -83, -102, 122, -111, 62, 112, 20, 3 }, { -127, -90, 28, -77, -48, -56, -10, 84, -41, 59, -115, 68, -74, -104, -119, -49, -37, -90, -57, 66, 108, 110, -62, -107, 88, 90, 29, -65, 74, -38, 95, 8, 120, 88, 96, -65, -109, 68, -63, -4, -16, 90, 7, 39, -56, -110, -100, 86, -39, -53, -89, -35, 127, -42, -48, -36, 53, -66, 109, -51, 51, -23, -12, 73 }, { -12, 78, 81, 30, 124, 22, 56, -112, 58, -99, 30, -98, 103, 66, 89, 92, -52, -20, 26, 82, -92, -18, 96, 7, 38, 21, -9, -25, -17, 4, 43, 15, 111, 103, -48, -50, -83, 52, 59, 103, 102, 83, -105, 87, 20, -120, 35, -7, -39, -24, 29, -39, -35, -87, 88, 120, 126, 19, 108, 34, -59, -20, 86, 47 }, { 19, -70, 36, 55, -42, -49, 33, 100, 105, -5, 89, 43, 3, -85, 60, -96, 43, -46, 86, -33, 120, -123, -99, -100, -34, 48, 82, -37, 34, 78, 127, 12, -39, -76, -26, 117, 74, -60, -68, -2, -37, -56, -6, 94, -27, 81, 32, -96, -19, -32, -77, 22, -56, -49, -38, -60, 45, -69, 40, 106, -106, -34, 101, -75 }, { 57, -92, -44, 8, -79, -88, -82, 58, -116, 93, 103, -127, 87, -121, -28, 31, -108, -14, -23, 38, 57, -83, -33, -110, 24, 6, 68, 124, -89, -35, -127, 5, -118, -78, -127, -35, 112, -34, 30, 24, -70, -71, 126, 39, -124, 122, -35, -97, -18, 25, 119, 79, 119, -65, 59, -20, -84, 120, -47, 4, -106, -125, -38, -113 }, { 18, -93, 34, -80, -43, 127, 57, -118, 24, -119, 25, 71, 59, -29, -108, -99, -122, 58, 44, 0, 42, -111, 25, 94, -36, 41, -64, -53, -78, -119, 85, 7, -45, -70, 81, -84, 71, -61, -68, -79, 112, 117, 19, 18, 70, 95, 108, -58, 48, 116, -89, 43, 66, 55, 37, -37, -60, 104, 47, -19, -56, 97, 73, 26 }, { 78, 4, -111, -36, 120, 111, -64, 46, 99, 125, -5, 97, -126, -21, 60, -78, -33, 89, 25, -60, 0, -49, 59, -118, 18, 3, -60, 30, 105, -92, -101, 15, 63, 50, 25, 2, -116, 78, -5, -25, -59, 74, -116, 64, -55, -121, 1, 69, 51, -119, 43, -6, -81, 14, 5, 84, -67, -73, 67, 24, 82, -37, 109, -93 }, { -44, -30, -64, -68, -21, 74, 124, 122, 114, -89, -91, -51, 89, 32, 96, -1, -101, -112, -94, 98, -24, -31, -50, 100, -72, 56, 24, 30, 105, 115, 15, 3, -67, 107, -18, 111, -38, -93, -11, 24, 36, 73, -23, 108, 14, -41, -65, 32, 51, 22, 95, 41, 85, -121, -35, -107, 0, 105, -112, 59, 48, -22, -84, 46 }, { 4, 38, 54, -84, -78, 24, -48, 8, -117, 78, -95, 24, 25, -32, -61, 26, -97, -74, 46, -120, -125, 27, 73, 107, -17, -21, -6, -52, 47, -68, 66, 5, -62, -12, -102, -127, 48, -69, -91, -81, -33, -13, -9, -12, -44, -73, 40, -58, 120, -120, 108, 101, 18, -14, -17, -93, 113, 49, 76, -4, -113, -91, -93, -52 }, { 28, -48, 70, -35, 123, -31, 16, -52, 72, 84, -51, 78, 104, 59, -102, -112, 29, 28, 25, 66, 12, 75, 26, -85, 56, -12, -4, -92, 49, 86, -27, 12, 44, -63, 108, 82, -76, -97, -41, 95, -48, -95, -115, 1, 64, -49, -97, 90, 65, 46, -114, -127, -92, 79, 100, 49, 116, -58, -106, 9, 117, -7, -91, 96 }, { 58, 26, 18, 76, 127, -77, -58, -87, -116, -44, 60, -32, -4, -76, -124, 4, -60, 82, -5, -100, -95, 18, 2, -53, -50, -96, -126, 105, 93, 19, 74, 13, 87, 125, -72, -10, 42, 14, 91, 44, 78, 52, 60, -59, -27, -37, -57, 17, -85, 31, -46, 113, 100, -117, 15, 108, -42, 12, 47, 63, 1, 11, -122, -3 } };
                for (int i = 0; i < recipients.length; ++i) {
                    final Transaction transaction = new Transaction((byte)0, (byte)0, 0, (short)0, Nxt.CREATOR_PUBLIC_KEY, recipients[i], amounts[i], 0, 0L, signatures[i]);
                    Nxt.transactions.put(transaction.getId(), transaction);
                }
                final Iterator i$4 = Nxt.transactions.values().iterator();
                while (i$4.hasNext()) {
                    final Transaction transaction = i$4.next();
                    transaction.index = Nxt.transactionCounter.incrementAndGet();
                    transaction.block = 2680262203532249785L;
                }
                Transaction.saveTransactions("transactions.nxt");
            }
            try {
                logMessage("Loading blocks...");
                Block.loadBlocks("blocks.nxt");
                logMessage("...Done");
            }
            catch (FileNotFoundException e12) {
                logMessage("blocks.nxt not found, starting from scratch");
                Nxt.blocks.clear();
                final Block block = new Block(-1, 0, 0L, Nxt.transactions.size(), 1000000000, 0, Nxt.transactions.size() * 128, null, Nxt.CREATOR_PUBLIC_KEY, new byte[64], new byte[] { 105, -44, 38, -60, -104, -73, 10, -58, -47, 103, -127, -128, 53, 101, 39, -63, -2, -32, 48, -83, 115, 47, -65, 118, 114, -62, 38, 109, 22, 106, 76, 8, -49, -113, -34, -76, 82, 79, -47, -76, -106, -69, -54, -85, 3, -6, 110, 103, 118, 15, 109, -92, 82, 37, 20, 2, 36, -112, 21, 72, 108, 72, 114, 17 });
                block.index = Nxt.blockCounter.incrementAndGet();
                Nxt.blocks.put(2680262203532249785L, block);
                int j = 0;
                for (final long transaction2 : Nxt.transactions.keySet()) {
                    block.transactions[j++] = transaction2;
                }
                Arrays.sort(block.transactions);
                final MessageDigest digest = getMessageDigest("SHA-256");
                for (j = 0; j < block.transactions.length; ++j) {
                    final Transaction transaction3 = Nxt.transactions.get(block.transactions[j]);
                    digest.update(transaction3.getBytes());
                    block.blockTransactions[j] = transaction3;
                }
                block.payloadHash = digest.digest();
                block.baseTarget = 153722867L;
                block.cumulativeDifficulty = BigInteger.ZERO;
                Nxt.lastBlock.set(block);
                Block.saveBlocks("blocks.nxt", false);
            }
            logMessage("Scanning blockchain...");
            final Map<Long, Block> loadedBlocks = new HashMap<Long, Block>(Nxt.blocks);
            Nxt.blocks.clear();
            Block curBlock;
            for (long curBlockId = 2680262203532249785L; (curBlock = loadedBlocks.get(curBlockId)) != null; curBlockId = curBlock.nextBlock) {
                curBlock.analyze();
            }
            logMessage("...Done");
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (Peer.getNumberOfConnectedPublicPeers() < Nxt.maxNumberOfConnectedPublicPeers) {
                            final Peer peer = Peer.getAnyPeer((ThreadLocalRandom.current().nextInt(2) == 0) ? 0 : 2, false);
                            if (peer != null) {
                                peer.connect();
                            }
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error connecting to peer", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 5L, TimeUnit.SECONDS);
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        final long curTime = System.currentTimeMillis();
                        for (final Peer peer : Nxt.peers.values()) {
                            if (peer.blacklistingTime > 0L && peer.blacklistingTime + Nxt.blacklistingPeriod <= curTime) {
                                peer.removeBlacklistedStatus();
                            }
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error un-blacklisting peer", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 1L, TimeUnit.SECONDS);
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                private final JSONObject getPeersRequest;
                
                {
                    (this.getPeersRequest = new JSONObject()).put((Object)"requestType", (Object)"getPeers");
                }
                
                @Override
                public void run() {
                    try {
                        final Peer peer = Peer.getAnyPeer(1, true);
                        if (peer != null) {
                            final JSONObject response = peer.send(this.getPeersRequest);
                            if (response != null) {
                                final JSONArray peers = (JSONArray)response.get((Object)"peers");
                                for (final Object peerAddress : peers) {
                                    final String address = ((String)peerAddress).trim();
                                    if (address.length() > 0) {
                                        Peer.addPeer(address, address);
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error requesting peers from a peer", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 5L, TimeUnit.SECONDS);
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                private final JSONObject getUnconfirmedTransactionsRequest;
                
                {
                    (this.getUnconfirmedTransactionsRequest = new JSONObject()).put((Object)"requestType", (Object)"getUnconfirmedTransactions");
                }
                
                @Override
                public void run() {
                    try {
                        final Peer peer = Peer.getAnyPeer(1, true);
                        if (peer != null) {
                            final JSONObject response = peer.send(this.getUnconfirmedTransactionsRequest);
                            if (response != null) {
                                Transaction.processTransactions(response, "unconfirmedTransactions");
                            }
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error processing unconfirmed transactions from peer", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 5L, TimeUnit.SECONDS);
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        final int curTime = Nxt.getEpochTime(System.currentTimeMillis());
                        final JSONArray removedUnconfirmedTransactions = new JSONArray();
                        final Iterator<Transaction> iterator = Nxt.unconfirmedTransactions.values().iterator();
                        while (iterator.hasNext()) {
                            final Transaction transaction = iterator.next();
                            if (transaction.timestamp + transaction.deadline * 60 < curTime || !transaction.validateAttachment()) {
                                iterator.remove();
                                final Account account = Nxt.accounts.get(transaction.getSenderAccountId());
                                account.addToUnconfirmedBalance((transaction.amount + transaction.fee) * 100L);
                                final JSONObject removedUnconfirmedTransaction = new JSONObject();
                                removedUnconfirmedTransaction.put((Object)"index", (Object)transaction.index);
                                removedUnconfirmedTransactions.add((Object)removedUnconfirmedTransaction);
                            }
                        }
                        if (removedUnconfirmedTransactions.size() > 0) {
                            final JSONObject response = new JSONObject();
                            response.put((Object)"response", (Object)"processNewData");
                            response.put((Object)"removedUnconfirmedTransactions", (Object)removedUnconfirmedTransactions);
                            for (final User user : Nxt.users.values()) {
                                user.send(response);
                            }
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error removing unconfirmed transactions", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 1L, TimeUnit.SECONDS);
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                private final JSONObject getCumulativeDifficultyRequest = new JSONObject();
                private final JSONObject getMilestoneBlockIdsRequest = new JSONObject();
                
                {
                    this.getCumulativeDifficultyRequest.put((Object)"requestType", (Object)"getCumulativeDifficulty");
                    this.getMilestoneBlockIdsRequest.put((Object)"requestType", (Object)"getMilestoneBlockIds");
                }
                
                @Override
                public void run() {
                    try {
                        final Peer peer = Peer.getAnyPeer(1, true);
                        if (peer != null) {
                            Nxt.lastBlockchainFeeder = peer;
                            JSONObject response = peer.send(this.getCumulativeDifficultyRequest);
                            if (response != null) {
                                BigInteger curCumulativeDifficulty = Nxt.lastBlock.get().cumulativeDifficulty;
                                final String peerCumulativeDifficulty = (String)response.get((Object)"cumulativeDifficulty");
                                if (peerCumulativeDifficulty == null) {
                                    return;
                                }
                                final BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
                                if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) > 0) {
                                    response = peer.send(this.getMilestoneBlockIdsRequest);
                                    if (response != null) {
                                        long commonBlockId = 2680262203532249785L;
                                        final JSONArray milestoneBlockIds = (JSONArray)response.get((Object)"milestoneBlockIds");
                                        for (final Object milestoneBlockId : milestoneBlockIds) {
                                            final long blockId = Nxt.parseUnsignedLong((String)milestoneBlockId);
                                            final Block block = Nxt.blocks.get(blockId);
                                            if (block != null) {
                                                commonBlockId = blockId;
                                                break;
                                            }
                                        }
                                        int i;
                                        int numberOfBlocks;
                                        do {
                                            final JSONObject request = new JSONObject();
                                            request.put((Object)"requestType", (Object)"getNextBlockIds");
                                            request.put((Object)"blockId", (Object)Nxt.convert(commonBlockId));
                                            response = peer.send(request);
                                            if (response == null) {
                                                return;
                                            }
                                            final JSONArray nextBlockIds = (JSONArray)response.get((Object)"nextBlockIds");
                                            numberOfBlocks = nextBlockIds.size();
                                            if (numberOfBlocks == 0) {
                                                return;
                                            }
                                            for (i = 0; i < numberOfBlocks; ++i) {
                                                final long blockId2 = Nxt.parseUnsignedLong((String)nextBlockIds.get(i));
                                                if (Nxt.blocks.get(blockId2) == null) {
                                                    break;
                                                }
                                                commonBlockId = blockId2;
                                            }
                                        } while (i == numberOfBlocks);
                                        if (Nxt.lastBlock.get().height - Nxt.blocks.get(commonBlockId).height < 720) {
                                            long curBlockId = commonBlockId;
                                            final LinkedList<Block> futureBlocks = new LinkedList<Block>();
                                            final HashMap<Long, Transaction> futureTransactions = new HashMap<Long, Transaction>();
                                            while (true) {
                                                final JSONObject request2 = new JSONObject();
                                                request2.put((Object)"requestType", (Object)"getNextBlocks");
                                                request2.put((Object)"blockId", (Object)Nxt.convert(curBlockId));
                                                response = peer.send(request2);
                                                if (response == null) {
                                                    break;
                                                }
                                                final JSONArray nextBlocks = (JSONArray)response.get((Object)"nextBlocks");
                                                numberOfBlocks = nextBlocks.size();
                                                if (numberOfBlocks == 0) {
                                                    break;
                                                }
                                                synchronized (Nxt.blocksAndTransactionsLock) {
                                                    for (i = 0; i < numberOfBlocks; ++i) {
                                                        final JSONObject blockData = (JSONObject)nextBlocks.get(i);
                                                        final Block block2 = Block.getBlock(blockData);
                                                        if (block2 == null) {
                                                            peer.blacklist();
                                                            return;
                                                        }
                                                        curBlockId = block2.getId();
                                                        boolean alreadyPushed = false;
                                                        if (block2.previousBlock == Nxt.lastBlock.get().getId()) {
                                                            final ByteBuffer buffer = ByteBuffer.allocate(224 + block2.payloadLength);
                                                            buffer.order(ByteOrder.LITTLE_ENDIAN);
                                                            buffer.put(block2.getBytes());
                                                            final JSONArray transactionsData = (JSONArray)blockData.get((Object)"transactions");
                                                            for (final Object transaction : transactionsData) {
                                                                buffer.put(Transaction.getTransaction((JSONObject)transaction).getBytes());
                                                            }
                                                            if (!Block.pushBlock(buffer, false)) {
                                                                peer.blacklist();
                                                                return;
                                                            }
                                                            alreadyPushed = true;
                                                        }
                                                        if (!alreadyPushed && Nxt.blocks.get(block2.getId()) == null && block2.transactions.length <= 255) {
                                                            futureBlocks.add(block2);
                                                            final JSONArray transactionsData2 = (JSONArray)blockData.get((Object)"transactions");
                                                            for (int j = 0; j < block2.transactions.length; ++j) {
                                                                final Transaction transaction2 = Transaction.getTransaction((JSONObject)transactionsData2.get(j));
                                                                block2.transactions[j] = transaction2.getId();
                                                                block2.blockTransactions[j] = transaction2;
                                                                futureTransactions.put(block2.transactions[j], transaction2);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if (!futureBlocks.isEmpty() && Nxt.lastBlock.get().height - Nxt.blocks.get(commonBlockId).height < 720) {
                                                synchronized (Nxt.blocksAndTransactionsLock) {
                                                    Block.saveBlocks("blocks.nxt.bak", true);
                                                    Transaction.saveTransactions("transactions.nxt.bak");
                                                    curCumulativeDifficulty = Nxt.lastBlock.get().cumulativeDifficulty;
                                                    while (Nxt.lastBlock.get().getId() != commonBlockId && Block.popLastBlock()) {}
                                                    if (Nxt.lastBlock.get().getId() == commonBlockId) {
                                                        for (final Block block3 : futureBlocks) {
                                                            if (block3.previousBlock == Nxt.lastBlock.get().getId()) {
                                                                final ByteBuffer buffer2 = ByteBuffer.allocate(224 + block3.payloadLength);
                                                                buffer2.order(ByteOrder.LITTLE_ENDIAN);
                                                                buffer2.put(block3.getBytes());
                                                                for (final Transaction transaction3 : block3.blockTransactions) {
                                                                    buffer2.put(transaction3.getBytes());
                                                                }
                                                                if (!Block.pushBlock(buffer2, false)) {
                                                                    break;
                                                                }
                                                                continue;
                                                            }
                                                        }
                                                    }
                                                    if (Nxt.lastBlock.get().cumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                                                        Block.loadBlocks("blocks.nxt.bak");
                                                        Transaction.loadTransactions("transactions.nxt.bak");
                                                        peer.blacklist();
                                                        Nxt.accounts.clear();
                                                        Nxt.aliases.clear();
                                                        Nxt.aliasIdToAliasMappings.clear();
                                                        Nxt.unconfirmedTransactions.clear();
                                                        Nxt.doubleSpendingTransactions.clear();
                                                        Nxt.logMessage("Re-scanning blockchain...");
                                                        final Map<Long, Block> loadedBlocks = new HashMap<Long, Block>(Nxt.blocks);
                                                        Nxt.blocks.clear();
                                                        Block currentBlock;
                                                        for (long currentBlockId = 2680262203532249785L; (currentBlock = loadedBlocks.get(currentBlockId)) != null; currentBlockId = currentBlock.nextBlock) {
                                                            currentBlock.analyze();
                                                        }
                                                        Nxt.logMessage("...Done");
                                                    }
                                                }
                                            }
                                            synchronized (Nxt.blocksAndTransactionsLock) {
                                                Block.saveBlocks("blocks.nxt", false);
                                                Transaction.saveTransactions("transactions.nxt");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error in milestone blocks processing thread", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 1L, TimeUnit.SECONDS);
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                private final ConcurrentMap<Account, Block> lastBlocks = new ConcurrentHashMap<Account, Block>();
                private final ConcurrentMap<Account, BigInteger> hits = new ConcurrentHashMap<Account, BigInteger>();
                
                @Override
                public void run() {
                    try {
                        final HashMap<Account, User> unlockedAccounts = new HashMap<Account, User>();
                        for (final User user : Nxt.users.values()) {
                            if (user.secretPhrase != null) {
                                final Account account = Nxt.accounts.get(Account.getId(user.publicKey));
                                if (account == null || account.getEffectiveBalance() <= 0) {
                                    continue;
                                }
                                unlockedAccounts.put(account, user);
                            }
                        }
                        for (final Map.Entry<Account, User> unlockedAccountEntry : unlockedAccounts.entrySet()) {
                            final Account account = unlockedAccountEntry.getKey();
                            final User user2 = unlockedAccountEntry.getValue();
                            final Block lastBlock = Nxt.lastBlock.get();
                            if (this.lastBlocks.get(account) != lastBlock) {
                                final long effectiveBalance = account.getEffectiveBalance();
                                if (effectiveBalance <= 0L) {
                                    continue;
                                }
                                final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                                byte[] generationSignatureHash;
                                if (lastBlock.height < 30000) {
                                    final byte[] generationSignature = Crypto.sign(lastBlock.generationSignature, user2.secretPhrase);
                                    generationSignatureHash = digest.digest(generationSignature);
                                }
                                else {
                                    digest.update(lastBlock.generationSignature);
                                    generationSignatureHash = digest.digest(user2.publicKey);
                                }
                                final BigInteger hit = new BigInteger(1, new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0] });
                                this.lastBlocks.put(account, lastBlock);
                                this.hits.put(account, hit);
                                final JSONObject response = new JSONObject();
                                response.put((Object)"response", (Object)"setBlockGenerationDeadline");
                                response.put((Object)"deadline", (Object)(hit.divide(BigInteger.valueOf(lastBlock.baseTarget).multiply(BigInteger.valueOf(effectiveBalance))).longValue() - (Nxt.getEpochTime(System.currentTimeMillis()) - lastBlock.timestamp)));
                                user2.send(response);
                            }
                            final int elapsedTime = Nxt.getEpochTime(System.currentTimeMillis()) - lastBlock.timestamp;
                            if (elapsedTime > 0) {
                                final BigInteger target = BigInteger.valueOf(lastBlock.baseTarget).multiply(BigInteger.valueOf(account.getEffectiveBalance())).multiply(BigInteger.valueOf(elapsedTime));
                                if (this.hits.get(account).compareTo(target) >= 0) {
                                    continue;
                                }
                                account.generateBlock(user2.secretPhrase);
                            }
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error in block generation thread", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 1L, TimeUnit.SECONDS);
            Nxt.scheduledThreadPool.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        final JSONArray transactionsData = new JSONArray();
                        for (final Transaction transaction : Nxt.nonBroadcastedTransactions.values()) {
                            if (Nxt.unconfirmedTransactions.get(transaction.id) == null && Nxt.transactions.get(transaction.id) == null) {
                                transactionsData.add((Object)transaction.getJSONObject());
                            }
                            else {
                                Nxt.nonBroadcastedTransactions.remove(transaction.id);
                            }
                        }
                        if (transactionsData.size() > 0) {
                            final JSONObject peerRequest = new JSONObject();
                            peerRequest.put((Object)"requestType", (Object)"processTransactions");
                            peerRequest.put((Object)"transactions", (Object)transactionsData);
                            Peer.sendToSomePeers(peerRequest);
                        }
                    }
                    catch (Exception e) {
                        Nxt.logDebugMessage("Error in transaction re-broadcasting thread", e);
                    }
                    catch (Throwable t) {
                        Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                        t.printStackTrace();
                        System.exit(1);
                    }
                }
            }, 0L, 60L, TimeUnit.SECONDS);
            logMessage("NRS 0.5.10 started successfully.");
        }
        catch (Exception e) {
            logMessage("Error initializing Nxt servlet", e);
            System.exit(1);
        }
    }
    
    public void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0L);
        User user = null;
        try {
            final String userPasscode = req.getParameter("user");
            if (userPasscode == null) {
                JSONObject response = new JSONObject();
                if (Nxt.allowedBotHosts != null && !Nxt.allowedBotHosts.contains(req.getRemoteHost())) {
                    response.put((Object)"errorCode", (Object)7);
                    response.put((Object)"errorDescription", (Object)"Not allowed");
                }
                else {
                    final String requestType = req.getParameter("requestType");
                    if (requestType == null) {
                        response.put((Object)"errorCode", (Object)1);
                        response.put((Object)"errorDescription", (Object)"Incorrect request");
                    }
                    else {
                        final String s = requestType;
                        switch (s) {
                            case "assignAlias": {
                                final String secretPhrase = req.getParameter("secretPhrase");
                                String alias = req.getParameter("alias");
                                String uri = req.getParameter("uri");
                                final String feeValue = req.getParameter("fee");
                                final String deadlineValue = req.getParameter("deadline");
                                final String referencedTransactionValue = req.getParameter("referencedTransaction");
                                if (secretPhrase == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"secretPhrase\" not specified");
                                }
                                else if (alias == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"alias\" not specified");
                                }
                                else if (uri == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"uri\" not specified");
                                }
                                else if (feeValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"fee\" not specified");
                                }
                                else if (deadlineValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"deadline\" not specified");
                                }
                                else {
                                    alias = alias.trim();
                                    if (alias.length() == 0 || alias.length() > 100) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"alias\" (length must be in [1..100] range)");
                                    }
                                    else {
                                        String normalizedAlias;
                                        int i;
                                        for (normalizedAlias = alias.toLowerCase(), i = 0; i < normalizedAlias.length() && "0123456789abcdefghijklmnopqrstuvwxyz".indexOf(normalizedAlias.charAt(i)) >= 0; ++i) {}
                                        if (i != normalizedAlias.length()) {
                                            response.put((Object)"errorCode", (Object)4);
                                            response.put((Object)"errorDescription", (Object)"Incorrect \"alias\" (must contain only digits and latin letters)");
                                        }
                                        else {
                                            uri = uri.trim();
                                            if (uri.length() > 1000) {
                                                response.put((Object)"errorCode", (Object)4);
                                                response.put((Object)"errorDescription", (Object)"Incorrect \"uri\" (length must be not longer than 1000 characters)");
                                            }
                                            else {
                                                try {
                                                    final int fee = Integer.parseInt(feeValue);
                                                    if (fee <= 0 || fee >= 1000000000L) {
                                                        throw new Exception();
                                                    }
                                                    try {
                                                        final short deadline = Short.parseShort(deadlineValue);
                                                        if (deadline < 1) {
                                                            throw new Exception();
                                                        }
                                                        final long referencedTransaction = (referencedTransactionValue == null) ? 0L : parseUnsignedLong(referencedTransactionValue);
                                                        final byte[] publicKey = Crypto.getPublicKey(secretPhrase);
                                                        final long accountId = Account.getId(publicKey);
                                                        final Account account = Nxt.accounts.get(accountId);
                                                        if (account == null) {
                                                            response.put((Object)"errorCode", (Object)6);
                                                            response.put((Object)"errorDescription", (Object)"Not enough funds");
                                                        }
                                                        else if (fee * 100L > account.getUnconfirmedBalance()) {
                                                            response.put((Object)"errorCode", (Object)6);
                                                            response.put((Object)"errorDescription", (Object)"Not enough funds");
                                                        }
                                                        else {
                                                            final Alias aliasData = Nxt.aliases.get(normalizedAlias);
                                                            if (aliasData != null && aliasData.account != account) {
                                                                response.put((Object)"errorCode", (Object)8);
                                                                response.put((Object)"errorDescription", (Object)("\"" + alias + "\" is already used"));
                                                            }
                                                            else {
                                                                final int timestamp = getEpochTime(System.currentTimeMillis());
                                                                final Transaction transaction = new Transaction((byte)1, (byte)1, timestamp, deadline, publicKey, 1739068987193023818L, 0, fee, referencedTransaction, new byte[64]);
                                                                transaction.attachment = new Transaction.MessagingAliasAssignmentAttachment(alias, uri);
                                                                transaction.sign(secretPhrase);
                                                                final JSONObject peerRequest = new JSONObject();
                                                                peerRequest.put((Object)"requestType", (Object)"processTransactions");
                                                                final JSONArray transactionsData = new JSONArray();
                                                                transactionsData.add((Object)transaction.getJSONObject());
                                                                peerRequest.put((Object)"transactions", (Object)transactionsData);
                                                                Peer.sendToSomePeers(peerRequest);
                                                                response.put((Object)"transaction", (Object)transaction.getStringId());
                                                                Nxt.nonBroadcastedTransactions.put(transaction.id, transaction);
                                                            }
                                                        }
                                                    }
                                                    catch (Exception e2) {
                                                        response.put((Object)"errorCode", (Object)4);
                                                        response.put((Object)"errorDescription", (Object)"Incorrect \"deadline\"");
                                                    }
                                                }
                                                catch (Exception e3) {
                                                    response.put((Object)"errorCode", (Object)4);
                                                    response.put((Object)"errorDescription", (Object)"Incorrect \"fee\"");
                                                }
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                            case "broadcastTransaction": {
                                final String transactionBytes = req.getParameter("transactionBytes");
                                if (transactionBytes == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"transactionBytes\" not specified");
                                }
                                else {
                                    try {
                                        final ByteBuffer buffer = ByteBuffer.wrap(convert(transactionBytes));
                                        buffer.order(ByteOrder.LITTLE_ENDIAN);
                                        final Transaction transaction2 = Transaction.getTransaction(buffer);
                                        final JSONObject peerRequest2 = new JSONObject();
                                        peerRequest2.put((Object)"requestType", (Object)"processTransactions");
                                        final JSONArray transactionsData2 = new JSONArray();
                                        transactionsData2.add((Object)transaction2.getJSONObject());
                                        peerRequest2.put((Object)"transactions", (Object)transactionsData2);
                                        Peer.sendToSomePeers(peerRequest2);
                                        response.put((Object)"transaction", (Object)transaction2.getStringId());
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"transactionBytes\"");
                                    }
                                }
                                break;
                            }
                            case "decodeHallmark": {
                                final String hallmarkValue = req.getParameter("hallmark");
                                if (hallmarkValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"hallmark\" not specified");
                                }
                                else {
                                    try {
                                        final byte[] hallmark = convert(hallmarkValue);
                                        final ByteBuffer buffer2 = ByteBuffer.wrap(hallmark);
                                        buffer2.order(ByteOrder.LITTLE_ENDIAN);
                                        final byte[] publicKey2 = new byte[32];
                                        buffer2.get(publicKey2);
                                        final int hostLength = buffer2.getShort();
                                        final byte[] hostBytes = new byte[hostLength];
                                        buffer2.get(hostBytes);
                                        final String host = new String(hostBytes, "UTF-8");
                                        final int weight = buffer2.getInt();
                                        final int date = buffer2.getInt();
                                        buffer2.get();
                                        final byte[] signature = new byte[64];
                                        buffer2.get(signature);
                                        response.put((Object)"account", (Object)convert(Account.getId(publicKey2)));
                                        response.put((Object)"host", (Object)host);
                                        response.put((Object)"weight", (Object)weight);
                                        final int year = date / 10000;
                                        final int month = date % 10000 / 100;
                                        final int day = date % 100;
                                        response.put((Object)"date", (Object)(((year < 10) ? "000" : ((year < 100) ? "00" : ((year < 1000) ? "0" : ""))) + year + "-" + ((month < 10) ? "0" : "") + month + "-" + ((day < 10) ? "0" : "") + day));
                                        final byte[] data = new byte[hallmark.length - 64];
                                        System.arraycopy(hallmark, 0, data, 0, data.length);
                                        response.put((Object)"valid", (Object)(host.length() <= 100 && weight > 0 && weight <= 1000000000L && Crypto.verify(signature, data, publicKey2)));
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"hallmark\"");
                                    }
                                }
                                break;
                            }
                            case "decodeToken": {
                                final String website = req.getParameter("website");
                                final String token = req.getParameter("token");
                                if (website == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"website\" not specified");
                                }
                                else if (token == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"token\" not specified");
                                }
                                else {
                                    final byte[] websiteBytes = website.trim().getBytes("UTF-8");
                                    final byte[] tokenBytes = new byte[100];
                                    int j = 0;
                                    int k = 0;
                                    try {
                                        while (j < token.length()) {
                                            final long number = Long.parseLong(token.substring(j, j + 8), 32);
                                            tokenBytes[k] = (byte)number;
                                            tokenBytes[k + 1] = (byte)(number >> 8);
                                            tokenBytes[k + 2] = (byte)(number >> 16);
                                            tokenBytes[k + 3] = (byte)(number >> 24);
                                            tokenBytes[k + 4] = (byte)(number >> 32);
                                            j += 8;
                                            k += 5;
                                        }
                                    }
                                    catch (Exception ex) {}
                                    if (j != 160) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"token\"");
                                    }
                                    else {
                                        final byte[] publicKey3 = new byte[32];
                                        System.arraycopy(tokenBytes, 0, publicKey3, 0, 32);
                                        final int timestamp2 = (tokenBytes[32] & 0xFF) | (tokenBytes[33] & 0xFF) << 8 | (tokenBytes[34] & 0xFF) << 16 | (tokenBytes[35] & 0xFF) << 24;
                                        final byte[] signature2 = new byte[64];
                                        System.arraycopy(tokenBytes, 36, signature2, 0, 64);
                                        final byte[] data2 = new byte[websiteBytes.length + 36];
                                        System.arraycopy(websiteBytes, 0, data2, 0, websiteBytes.length);
                                        System.arraycopy(tokenBytes, 0, data2, websiteBytes.length, 36);
                                        final boolean valid = Crypto.verify(signature2, data2, publicKey3);
                                        response.put((Object)"account", (Object)convert(Account.getId(publicKey3)));
                                        response.put((Object)"timestamp", (Object)timestamp2);
                                        response.put((Object)"valid", (Object)valid);
                                    }
                                }
                                break;
                            }
                            case "getAccountBlockIds": {
                                final String account2 = req.getParameter("account");
                                final String timestampValue = req.getParameter("timestamp");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else if (timestampValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"timestamp\" not specified");
                                }
                                else {
                                    try {
                                        final Account accountData = Nxt.accounts.get(parseUnsignedLong(account2));
                                        if (accountData == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown account");
                                        }
                                        else {
                                            try {
                                                final int timestamp3 = Integer.parseInt(timestampValue);
                                                if (timestamp3 < 0) {
                                                    throw new Exception();
                                                }
                                                final PriorityQueue<Block> sortedBlocks = new PriorityQueue<Block>(11, Block.heightComparator);
                                                final byte[] accountPublicKey = accountData.publicKey.get();
                                                for (final Block block : Nxt.blocks.values()) {
                                                    if (block.timestamp >= timestamp3 && Arrays.equals(block.generatorPublicKey, accountPublicKey)) {
                                                        sortedBlocks.offer(block);
                                                    }
                                                }
                                                final JSONArray blockIds = new JSONArray();
                                                while (!sortedBlocks.isEmpty()) {
                                                    blockIds.add((Object)sortedBlocks.poll().getStringId());
                                                }
                                                response.put((Object)"blockIds", (Object)blockIds);
                                            }
                                            catch (Exception e5) {
                                                response.put((Object)"errorCode", (Object)4);
                                                response.put((Object)"errorDescription", (Object)"Incorrect \"timestamp\"");
                                            }
                                        }
                                    }
                                    catch (Exception e6) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "getAccountId": {
                                final String secretPhrase = req.getParameter("secretPhrase");
                                if (secretPhrase == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"secretPhrase\" not specified");
                                }
                                else {
                                    final byte[] publicKeyHash = getMessageDigest("SHA-256").digest(Crypto.getPublicKey(secretPhrase));
                                    final BigInteger bigInteger = new BigInteger(1, new byte[] { publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4], publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0] });
                                    response.put((Object)"accountId", (Object)bigInteger.toString());
                                }
                                break;
                            }
                            case "getAccountPublicKey": {
                                final String account2 = req.getParameter("account");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else {
                                    try {
                                        final Account accountData2 = Nxt.accounts.get(parseUnsignedLong(account2));
                                        if (accountData2 == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown account");
                                        }
                                        else if (accountData2.publicKey.get() != null) {
                                            response.put((Object)"publicKey", (Object)convert(accountData2.publicKey.get()));
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "getAccountTransactionIds": {
                                final String account2 = req.getParameter("account");
                                final String timestampValue = req.getParameter("timestamp");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else if (timestampValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"timestamp\" not specified");
                                }
                                else {
                                    try {
                                        final Account accountData = Nxt.accounts.get(parseUnsignedLong(account2));
                                        if (accountData == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown account");
                                        }
                                        else {
                                            try {
                                                final int timestamp3 = Integer.parseInt(timestampValue);
                                                if (timestamp3 < 0) {
                                                    throw new Exception();
                                                }
                                                int type;
                                                try {
                                                    type = Integer.parseInt(req.getParameter("type"));
                                                }
                                                catch (Exception e7) {
                                                    type = -1;
                                                }
                                                int subtype;
                                                try {
                                                    subtype = Integer.parseInt(req.getParameter("subtype"));
                                                }
                                                catch (Exception e7) {
                                                    subtype = -1;
                                                }
                                                final PriorityQueue<Transaction> sortedTransactions = new PriorityQueue<Transaction>(11, Transaction.timestampComparator);
                                                final byte[] accountPublicKey2 = accountData.publicKey.get();
                                                for (final Transaction transaction3 : Nxt.transactions.values()) {
                                                    if ((transaction3.recipient == accountData.id || Arrays.equals(transaction3.senderPublicKey, accountPublicKey2)) && (type < 0 || transaction3.type == type) && (subtype < 0 || transaction3.subtype == subtype) && Nxt.blocks.get(transaction3.block).timestamp >= timestamp3) {
                                                        sortedTransactions.offer(transaction3);
                                                    }
                                                }
                                                final JSONArray transactionIds = new JSONArray();
                                                while (!sortedTransactions.isEmpty()) {
                                                    transactionIds.add((Object)sortedTransactions.poll().getStringId());
                                                }
                                                response.put((Object)"transactionIds", (Object)transactionIds);
                                            }
                                            catch (Exception e5) {
                                                response.put((Object)"errorCode", (Object)4);
                                                response.put((Object)"errorDescription", (Object)"Incorrect \"timestamp\"");
                                            }
                                        }
                                    }
                                    catch (Exception e6) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "getAlias": {
                                final String alias2 = req.getParameter("alias");
                                if (alias2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"alias\" not specified");
                                }
                                else {
                                    try {
                                        final Alias aliasData2 = Nxt.aliasIdToAliasMappings.get(parseUnsignedLong(alias2));
                                        if (aliasData2 == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown alias");
                                        }
                                        else {
                                            response.put((Object)"account", (Object)convert(aliasData2.account.id));
                                            response.put((Object)"alias", (Object)aliasData2.alias);
                                            if (aliasData2.uri.length() > 0) {
                                                response.put((Object)"uri", (Object)aliasData2.uri);
                                            }
                                            response.put((Object)"timestamp", (Object)aliasData2.timestamp);
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"alias\"");
                                    }
                                }
                                break;
                            }
                            case "getAliasId": {
                                final String alias2 = req.getParameter("alias");
                                if (alias2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"alias\" not specified");
                                }
                                else {
                                    final Alias aliasData2 = Nxt.aliases.get(alias2.toLowerCase());
                                    if (aliasData2 == null) {
                                        response.put((Object)"errorCode", (Object)5);
                                        response.put((Object)"errorDescription", (Object)"Unknown alias");
                                    }
                                    else {
                                        response.put((Object)"id", (Object)convert(aliasData2.id));
                                    }
                                }
                                break;
                            }
                            case "getAliasIds": {
                                final String timestampValue2 = req.getParameter("timestamp");
                                if (timestampValue2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"timestamp\" not specified");
                                }
                                else {
                                    try {
                                        final int timestamp4 = Integer.parseInt(timestampValue2);
                                        if (timestamp4 < 0) {
                                            throw new Exception();
                                        }
                                        final JSONArray aliasIds = new JSONArray();
                                        for (final Map.Entry<Long, Alias> aliasEntry : Nxt.aliasIdToAliasMappings.entrySet()) {
                                            if (aliasEntry.getValue().timestamp >= timestamp4) {
                                                aliasIds.add((Object)convert(aliasEntry.getKey()));
                                            }
                                        }
                                        response.put((Object)"aliasIds", (Object)aliasIds);
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"timestamp\"");
                                    }
                                }
                                break;
                            }
                            case "getAliasURI": {
                                final String alias2 = req.getParameter("alias");
                                if (alias2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"alias\" not specified");
                                }
                                else {
                                    final Alias aliasData2 = Nxt.aliases.get(alias2.toLowerCase());
                                    if (aliasData2 == null) {
                                        response.put((Object)"errorCode", (Object)5);
                                        response.put((Object)"errorDescription", (Object)"Unknown alias");
                                    }
                                    else if (aliasData2.uri.length() > 0) {
                                        response.put((Object)"uri", (Object)aliasData2.uri);
                                    }
                                }
                                break;
                            }
                            case "getBalance": {
                                final String account2 = req.getParameter("account");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else {
                                    try {
                                        final Account accountData2 = Nxt.accounts.get(parseUnsignedLong(account2));
                                        if (accountData2 == null) {
                                            response.put((Object)"balance", (Object)0);
                                            response.put((Object)"unconfirmedBalance", (Object)0);
                                            response.put((Object)"effectiveBalance", (Object)0);
                                        }
                                        else {
                                            synchronized (accountData2) {
                                                response.put((Object)"balance", (Object)accountData2.getBalance());
                                                response.put((Object)"unconfirmedBalance", (Object)accountData2.getUnconfirmedBalance());
                                                response.put((Object)"effectiveBalance", (Object)(accountData2.getEffectiveBalance() * 100L));
                                            }
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "getBlock": {
                                final String block2 = req.getParameter("block");
                                if (block2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"block\" not specified");
                                }
                                else {
                                    try {
                                        final Block blockData = Nxt.blocks.get(parseUnsignedLong(block2));
                                        if (blockData == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown block");
                                        }
                                        else {
                                            response.put((Object)"height", (Object)blockData.height);
                                            response.put((Object)"generator", (Object)convert(blockData.getGeneratorAccountId()));
                                            response.put((Object)"timestamp", (Object)blockData.timestamp);
                                            response.put((Object)"numberOfTransactions", (Object)blockData.transactions.length);
                                            response.put((Object)"totalAmount", (Object)blockData.totalAmount);
                                            response.put((Object)"totalFee", (Object)blockData.totalFee);
                                            response.put((Object)"payloadLength", (Object)blockData.payloadLength);
                                            response.put((Object)"version", (Object)blockData.version);
                                            response.put((Object)"baseTarget", (Object)convert(blockData.baseTarget));
                                            if (blockData.previousBlock != 0L) {
                                                response.put((Object)"previousBlock", (Object)convert(blockData.previousBlock));
                                            }
                                            if (blockData.nextBlock != 0L) {
                                                response.put((Object)"nextBlock", (Object)convert(blockData.nextBlock));
                                            }
                                            response.put((Object)"payloadHash", (Object)convert(blockData.payloadHash));
                                            response.put((Object)"generationSignature", (Object)convert(blockData.generationSignature));
                                            if (blockData.version > 1) {
                                                response.put((Object)"previousBlockHash", (Object)convert(blockData.previousBlockHash));
                                            }
                                            response.put((Object)"blockSignature", (Object)convert(blockData.blockSignature));
                                            final JSONArray transactions = new JSONArray();
                                            for (final long transactionId : blockData.transactions) {
                                                transactions.add((Object)convert(transactionId));
                                            }
                                            response.put((Object)"transactions", (Object)transactions);
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"block\"");
                                    }
                                }
                                break;
                            }
                            case "getConstants": {
                                response.put((Object)"genesisBlockId", (Object)convert(2680262203532249785L));
                                response.put((Object)"genesisAccountId", (Object)convert(1739068987193023818L));
                                response.put((Object)"maxBlockPayloadLength", (Object)32640);
                                response.put((Object)"maxArbitraryMessageLength", (Object)1000);
                                final JSONArray transactionTypes = new JSONArray();
                                JSONObject transactionType = new JSONObject();
                                transactionType.put((Object)"value", (Object)(byte)0);
                                transactionType.put((Object)"description", (Object)"Payment");
                                JSONArray subtypes = new JSONArray();
                                JSONObject subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)0);
                                subtype2.put((Object)"description", (Object)"Ordinary payment");
                                subtypes.add((Object)subtype2);
                                transactionType.put((Object)"subtypes", (Object)subtypes);
                                transactionTypes.add((Object)transactionType);
                                transactionType = new JSONObject();
                                transactionType.put((Object)"value", (Object)(byte)1);
                                transactionType.put((Object)"description", (Object)"Messaging");
                                subtypes = new JSONArray();
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)0);
                                subtype2.put((Object)"description", (Object)"Arbitrary message");
                                subtypes.add((Object)subtype2);
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)1);
                                subtype2.put((Object)"description", (Object)"Alias assignment");
                                subtypes.add((Object)subtype2);
                                transactionType.put((Object)"subtypes", (Object)subtypes);
                                transactionTypes.add((Object)transactionType);
                                transactionType = new JSONObject();
                                transactionType.put((Object)"value", (Object)(byte)2);
                                transactionType.put((Object)"description", (Object)"Colored coins");
                                subtypes = new JSONArray();
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)0);
                                subtype2.put((Object)"description", (Object)"Asset issuance");
                                subtypes.add((Object)subtype2);
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)1);
                                subtype2.put((Object)"description", (Object)"Asset transfer");
                                subtypes.add((Object)subtype2);
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)2);
                                subtype2.put((Object)"description", (Object)"Ask order placement");
                                subtypes.add((Object)subtype2);
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)3);
                                subtype2.put((Object)"description", (Object)"Bid order placement");
                                subtypes.add((Object)subtype2);
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)4);
                                subtype2.put((Object)"description", (Object)"Ask order cancellation");
                                subtypes.add((Object)subtype2);
                                subtype2 = new JSONObject();
                                subtype2.put((Object)"value", (Object)(byte)5);
                                subtype2.put((Object)"description", (Object)"Bid order cancellation");
                                subtypes.add((Object)subtype2);
                                transactionType.put((Object)"subtypes", (Object)subtypes);
                                transactionTypes.add((Object)transactionType);
                                response.put((Object)"transactionTypes", (Object)transactionTypes);
                                final JSONArray peerStates = new JSONArray();
                                JSONObject peerState = new JSONObject();
                                peerState.put((Object)"value", (Object)0);
                                peerState.put((Object)"description", (Object)"Non-connected");
                                peerStates.add((Object)peerState);
                                peerState = new JSONObject();
                                peerState.put((Object)"value", (Object)1);
                                peerState.put((Object)"description", (Object)"Connected");
                                peerStates.add((Object)peerState);
                                peerState = new JSONObject();
                                peerState.put((Object)"value", (Object)2);
                                peerState.put((Object)"description", (Object)"Disconnected");
                                peerStates.add((Object)peerState);
                                response.put((Object)"peerStates", (Object)peerStates);
                                break;
                            }
                            case "getGuaranteedBalance": {
                                final String account2 = req.getParameter("account");
                                final String numberOfConfirmationsValue = req.getParameter("numberOfConfirmations");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else if (numberOfConfirmationsValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"numberOfConfirmations\" not specified");
                                }
                                else {
                                    try {
                                        final Account accountData = Nxt.accounts.get(parseUnsignedLong(account2));
                                        if (accountData == null) {
                                            response.put((Object)"guaranteedBalance", (Object)0);
                                        }
                                        else {
                                            try {
                                                final int numberOfConfirmations = Integer.parseInt(numberOfConfirmationsValue);
                                                response.put((Object)"guaranteedBalance", (Object)accountData.getGuaranteedBalance(numberOfConfirmations));
                                            }
                                            catch (Exception e5) {
                                                response.put((Object)"errorCode", (Object)4);
                                                response.put((Object)"errorDescription", (Object)"Incorrect \"numberOfConfirmations\"");
                                            }
                                        }
                                    }
                                    catch (Exception e6) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "getMyInfo": {
                                response.put((Object)"host", (Object)req.getRemoteHost());
                                response.put((Object)"address", (Object)req.getRemoteAddr());
                                break;
                            }
                            case "getPeer": {
                                final String peer = req.getParameter("peer");
                                if (peer == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"peer\" not specified");
                                }
                                else {
                                    final Peer peerData = Nxt.peers.get(peer);
                                    if (peerData == null) {
                                        response.put((Object)"errorCode", (Object)5);
                                        response.put((Object)"errorDescription", (Object)"Unknown peer");
                                    }
                                    else {
                                        response.put((Object)"state", (Object)peerData.state);
                                        response.put((Object)"announcedAddress", (Object)peerData.announcedAddress);
                                        if (peerData.hallmark != null) {
                                            response.put((Object)"hallmark", (Object)peerData.hallmark);
                                        }
                                        response.put((Object)"weight", (Object)peerData.getWeight());
                                        response.put((Object)"downloadedVolume", (Object)peerData.downloadedVolume);
                                        response.put((Object)"uploadedVolume", (Object)peerData.uploadedVolume);
                                        response.put((Object)"application", (Object)peerData.application);
                                        response.put((Object)"version", (Object)peerData.version);
                                        response.put((Object)"platform", (Object)peerData.platform);
                                    }
                                }
                                break;
                            }
                            case "getPeers": {
                                final JSONArray peers = new JSONArray();
                                peers.addAll((Collection)Nxt.peers.keySet());
                                response.put((Object)"peers", (Object)peers);
                                break;
                            }
                            case "getState": {
                                response.put((Object)"version", (Object)"0.5.10");
                                response.put((Object)"time", (Object)getEpochTime(System.currentTimeMillis()));
                                response.put((Object)"lastBlock", (Object)Nxt.lastBlock.get().getStringId());
                                response.put((Object)"cumulativeDifficulty", (Object)Nxt.lastBlock.get().cumulativeDifficulty.toString());
                                long totalEffectiveBalance = 0L;
                                for (final Account account3 : Nxt.accounts.values()) {
                                    final long effectiveBalance = account3.getEffectiveBalance();
                                    if (effectiveBalance > 0L) {
                                        totalEffectiveBalance += effectiveBalance;
                                    }
                                }
                                response.put((Object)"totalEffectiveBalance", (Object)(totalEffectiveBalance * 100L));
                                response.put((Object)"numberOfBlocks", (Object)Nxt.blocks.size());
                                response.put((Object)"numberOfTransactions", (Object)Nxt.transactions.size());
                                response.put((Object)"numberOfAccounts", (Object)Nxt.accounts.size());
                                response.put((Object)"numberOfAssets", (Object)Nxt.assets.size());
                                response.put((Object)"numberOfOrders", (Object)(Nxt.askOrders.size() + Nxt.bidOrders.size()));
                                response.put((Object)"numberOfAliases", (Object)Nxt.aliases.size());
                                response.put((Object)"numberOfPeers", (Object)Nxt.peers.size());
                                response.put((Object)"numberOfUsers", (Object)Nxt.users.size());
                                response.put((Object)"lastBlockchainFeeder", (Object)((Nxt.lastBlockchainFeeder == null) ? null : Nxt.lastBlockchainFeeder.announcedAddress));
                                response.put((Object)"availableProcessors", (Object)Runtime.getRuntime().availableProcessors());
                                response.put((Object)"maxMemory", (Object)Runtime.getRuntime().maxMemory());
                                response.put((Object)"totalMemory", (Object)Runtime.getRuntime().totalMemory());
                                response.put((Object)"freeMemory", (Object)Runtime.getRuntime().freeMemory());
                                break;
                            }
                            case "getTime": {
                                response.put((Object)"time", (Object)getEpochTime(System.currentTimeMillis()));
                                break;
                            }
                            case "getTransaction": {
                                final String transaction4 = req.getParameter("transaction");
                                if (transaction4 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"transaction\" not specified");
                                }
                                else {
                                    try {
                                        final long transactionId2 = parseUnsignedLong(transaction4);
                                        Transaction transactionData = Nxt.transactions.get(transactionId2);
                                        if (transactionData == null) {
                                            transactionData = Nxt.unconfirmedTransactions.get(transactionId2);
                                            if (transactionData == null) {
                                                response.put((Object)"errorCode", (Object)5);
                                                response.put((Object)"errorDescription", (Object)"Unknown transaction");
                                            }
                                            else {
                                                response = transactionData.getJSONObject();
                                                response.put((Object)"sender", (Object)convert(transactionData.getSenderAccountId()));
                                            }
                                        }
                                        else {
                                            response = transactionData.getJSONObject();
                                            response.put((Object)"sender", (Object)convert(transactionData.getSenderAccountId()));
                                            final Block block3 = Nxt.blocks.get(transactionData.block);
                                            response.put((Object)"block", (Object)block3.getStringId());
                                            response.put((Object)"confirmations", (Object)(Nxt.lastBlock.get().height - block3.height + 1));
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"transaction\"");
                                    }
                                }
                                break;
                            }
                            case "getTransactionBytes": {
                                final String transaction4 = req.getParameter("transaction");
                                if (transaction4 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"transaction\" not specified");
                                }
                                else {
                                    try {
                                        final long transactionId2 = parseUnsignedLong(transaction4);
                                        Transaction transactionData = Nxt.transactions.get(transactionId2);
                                        if (transactionData == null) {
                                            transactionData = Nxt.unconfirmedTransactions.get(transactionId2);
                                            if (transactionData == null) {
                                                response.put((Object)"errorCode", (Object)5);
                                                response.put((Object)"errorDescription", (Object)"Unknown transaction");
                                            }
                                            else {
                                                response.put((Object)"bytes", (Object)convert(transactionData.getBytes()));
                                            }
                                        }
                                        else {
                                            response.put((Object)"bytes", (Object)convert(transactionData.getBytes()));
                                            final Block block3 = Nxt.blocks.get(transactionData.block);
                                            response.put((Object)"confirmations", (Object)(Nxt.lastBlock.get().height - block3.height + 1));
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"transaction\"");
                                    }
                                }
                                break;
                            }
                            case "getUnconfirmedTransactionIds": {
                                final JSONArray transactionIds2 = new JSONArray();
                                for (final Transaction transaction2 : Nxt.unconfirmedTransactions.values()) {
                                    transactionIds2.add((Object)transaction2.getStringId());
                                }
                                response.put((Object)"unconfirmedTransactionIds", (Object)transactionIds2);
                                break;
                            }
                            case "getAccountCurrentAskOrderIds": {
                                final String account2 = req.getParameter("account");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else {
                                    try {
                                        final Account accountData2 = Nxt.accounts.get(parseUnsignedLong(account2));
                                        if (accountData2 == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown account");
                                        }
                                        else {
                                            long assetId;
                                            boolean assetIsNotUsed;
                                            try {
                                                assetId = parseUnsignedLong(req.getParameter("asset"));
                                                assetIsNotUsed = false;
                                            }
                                            catch (Exception e8) {
                                                assetId = 0L;
                                                assetIsNotUsed = true;
                                            }
                                            final JSONArray orderIds = new JSONArray();
                                            for (final AskOrder askOrder : Nxt.askOrders.values()) {
                                                if ((assetIsNotUsed || askOrder.asset == assetId) && askOrder.account == accountData2) {
                                                    orderIds.add((Object)convert(askOrder.id));
                                                }
                                            }
                                            response.put((Object)"askOrderIds", (Object)orderIds);
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "getAccountCurrentBidOrderIds": {
                                final String account2 = req.getParameter("account");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else {
                                    try {
                                        final Account accountData2 = Nxt.accounts.get(parseUnsignedLong(account2));
                                        if (accountData2 == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown account");
                                        }
                                        else {
                                            long assetId;
                                            boolean assetIsNotUsed;
                                            try {
                                                assetId = parseUnsignedLong(req.getParameter("asset"));
                                                assetIsNotUsed = false;
                                            }
                                            catch (Exception e8) {
                                                assetId = 0L;
                                                assetIsNotUsed = true;
                                            }
                                            final JSONArray orderIds = new JSONArray();
                                            for (final BidOrder bidOrder : Nxt.bidOrders.values()) {
                                                if ((assetIsNotUsed || bidOrder.asset == assetId) && bidOrder.account == accountData2) {
                                                    orderIds.add((Object)convert(bidOrder.id));
                                                }
                                            }
                                            response.put((Object)"bidOrderIds", (Object)orderIds);
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "getAskOrder": {
                                final String order = req.getParameter("order");
                                if (order == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"order\" not specified");
                                }
                                else {
                                    try {
                                        final AskOrder orderData = Nxt.askOrders.get(parseUnsignedLong(order));
                                        if (orderData == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown ask order");
                                        }
                                        else {
                                            response.put((Object)"account", (Object)convert(orderData.account.id));
                                            response.put((Object)"asset", (Object)convert(orderData.asset));
                                            response.put((Object)"quantity", (Object)orderData.quantity);
                                            response.put((Object)"price", (Object)orderData.price);
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"order\"");
                                    }
                                }
                                break;
                            }
                            case "getAskOrderIds": {
                                final JSONArray orderIds2 = new JSONArray();
                                for (final Long orderId : Nxt.askOrders.keySet()) {
                                    orderIds2.add((Object)convert(orderId));
                                }
                                response.put((Object)"askOrderIds", (Object)orderIds2);
                                break;
                            }
                            case "getBidOrder": {
                                final String order = req.getParameter("order");
                                if (order == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"order\" not specified");
                                }
                                else {
                                    try {
                                        final BidOrder orderData2 = Nxt.bidOrders.get(parseUnsignedLong(order));
                                        if (orderData2 == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown bid order");
                                        }
                                        else {
                                            response.put((Object)"account", (Object)convert(orderData2.account.id));
                                            response.put((Object)"asset", (Object)convert(orderData2.asset));
                                            response.put((Object)"quantity", (Object)orderData2.quantity);
                                            response.put((Object)"price", (Object)orderData2.price);
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"order\"");
                                    }
                                }
                                break;
                            }
                            case "getBidOrderIds": {
                                final JSONArray orderIds2 = new JSONArray();
                                for (final Long orderId : Nxt.bidOrders.keySet()) {
                                    orderIds2.add((Object)convert(orderId));
                                }
                                response.put((Object)"bidOrderIds", (Object)orderIds2);
                                break;
                            }
                            case "listAccountAliases": {
                                final String account2 = req.getParameter("account");
                                if (account2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"account\" not specified");
                                }
                                else {
                                    try {
                                        final long accountId2 = parseUnsignedLong(account2);
                                        final Account accountData3 = Nxt.accounts.get(accountId2);
                                        if (accountData3 == null) {
                                            response.put((Object)"errorCode", (Object)5);
                                            response.put((Object)"errorDescription", (Object)"Unknown account");
                                        }
                                        else {
                                            final JSONArray aliases = new JSONArray();
                                            for (final Alias alias3 : Nxt.aliases.values()) {
                                                if (alias3.account.id == accountId2) {
                                                    final JSONObject aliasData3 = new JSONObject();
                                                    aliasData3.put((Object)"alias", (Object)alias3.alias);
                                                    aliasData3.put((Object)"uri", (Object)alias3.uri);
                                                    aliases.add((Object)aliasData3);
                                                }
                                            }
                                            response.put((Object)"aliases", (Object)aliases);
                                        }
                                    }
                                    catch (Exception e4) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"account\"");
                                    }
                                }
                                break;
                            }
                            case "markHost": {
                                final String secretPhrase = req.getParameter("secretPhrase");
                                final String host2 = req.getParameter("host");
                                final String weightValue = req.getParameter("weight");
                                final String dateValue = req.getParameter("date");
                                if (secretPhrase == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"secretPhrase\" not specified");
                                }
                                else if (host2 == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"host\" not specified");
                                }
                                else if (weightValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"weight\" not specified");
                                }
                                else if (dateValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"date\" not specified");
                                }
                                else if (host2.length() > 100) {
                                    response.put((Object)"errorCode", (Object)4);
                                    response.put((Object)"errorDescription", (Object)"Incorrect \"host\" (the length exceeds 100 chars limit)");
                                }
                                else {
                                    try {
                                        final int weight2 = Integer.parseInt(weightValue);
                                        if (weight2 <= 0 || weight2 > 1000000000L) {
                                            throw new Exception();
                                        }
                                        try {
                                            final int date2 = Integer.parseInt(dateValue.substring(0, 4)) * 10000 + Integer.parseInt(dateValue.substring(5, 7)) * 100 + Integer.parseInt(dateValue.substring(8, 10));
                                            final byte[] publicKey3 = Crypto.getPublicKey(secretPhrase);
                                            final byte[] hostBytes2 = host2.getBytes("UTF-8");
                                            final ByteBuffer buffer3 = ByteBuffer.allocate(34 + hostBytes2.length + 4 + 4 + 1);
                                            buffer3.order(ByteOrder.LITTLE_ENDIAN);
                                            buffer3.put(publicKey3);
                                            buffer3.putShort((short)hostBytes2.length);
                                            buffer3.put(hostBytes2);
                                            buffer3.putInt(weight2);
                                            buffer3.putInt(date2);
                                            final byte[] data2 = buffer3.array();
                                            byte[] signature3;
                                            do {
                                                data2[data2.length - 1] = (byte)ThreadLocalRandom.current().nextInt();
                                                signature3 = Crypto.sign(data2, secretPhrase);
                                            } while (!Crypto.verify(signature3, data2, publicKey3));
                                            response.put((Object)"hallmark", (Object)(convert(data2) + convert(signature3)));
                                        }
                                        catch (Exception e8) {
                                            response.put((Object)"errorCode", (Object)4);
                                            response.put((Object)"errorDescription", (Object)"Incorrect \"date\"");
                                        }
                                    }
                                    catch (Exception e9) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"weight\"");
                                    }
                                }
                                break;
                            }
                            case "sendMessage": {
                                final String secretPhrase = req.getParameter("secretPhrase");
                                final String recipientValue = req.getParameter("recipient");
                                final String messageValue = req.getParameter("message");
                                final String feeValue = req.getParameter("fee");
                                final String deadlineValue = req.getParameter("deadline");
                                final String referencedTransactionValue = req.getParameter("referencedTransaction");
                                if (secretPhrase == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"secretPhrase\" not specified");
                                }
                                else if (recipientValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"recipient\" not specified");
                                }
                                else if (messageValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"message\" not specified");
                                }
                                else if (feeValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"fee\" not specified");
                                }
                                else if (deadlineValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"deadline\" not specified");
                                }
                                else {
                                    try {
                                        final long recipient = parseUnsignedLong(recipientValue);
                                        try {
                                            final byte[] message = convert(messageValue);
                                            if (message.length > 1000) {
                                                response.put((Object)"errorCode", (Object)4);
                                                response.put((Object)"errorDescription", (Object)"Incorrect \"message\" (length must be not longer than 1000 bytes)");
                                            }
                                            else {
                                                try {
                                                    final int fee2 = Integer.parseInt(feeValue);
                                                    if (fee2 <= 0 || fee2 >= 1000000000L) {
                                                        throw new Exception();
                                                    }
                                                    try {
                                                        final short deadline2 = Short.parseShort(deadlineValue);
                                                        if (deadline2 < 1) {
                                                            throw new Exception();
                                                        }
                                                        final long referencedTransaction2 = (referencedTransactionValue == null) ? 0L : parseUnsignedLong(referencedTransactionValue);
                                                        final byte[] publicKey4 = Crypto.getPublicKey(secretPhrase);
                                                        final Account account4 = Nxt.accounts.get(Account.getId(publicKey4));
                                                        if (account4 == null || fee2 * 100L > account4.getUnconfirmedBalance()) {
                                                            response.put((Object)"errorCode", (Object)6);
                                                            response.put((Object)"errorDescription", (Object)"Not enough funds");
                                                        }
                                                        else {
                                                            final int timestamp5 = getEpochTime(System.currentTimeMillis());
                                                            final Transaction transaction5 = new Transaction((byte)1, (byte)0, timestamp5, deadline2, publicKey4, recipient, 0, fee2, referencedTransaction2, new byte[64]);
                                                            transaction5.attachment = new Transaction.MessagingArbitraryMessageAttachment(message);
                                                            transaction5.sign(secretPhrase);
                                                            final JSONObject peerRequest3 = new JSONObject();
                                                            peerRequest3.put((Object)"requestType", (Object)"processTransactions");
                                                            final JSONArray transactionsData3 = new JSONArray();
                                                            transactionsData3.add((Object)transaction5.getJSONObject());
                                                            peerRequest3.put((Object)"transactions", (Object)transactionsData3);
                                                            Peer.sendToSomePeers(peerRequest3);
                                                            response.put((Object)"transaction", (Object)transaction5.getStringId());
                                                            response.put((Object)"bytes", (Object)convert(transaction5.getBytes()));
                                                            Nxt.nonBroadcastedTransactions.put(transaction5.id, transaction5);
                                                        }
                                                    }
                                                    catch (Exception e10) {
                                                        response.put((Object)"errorCode", (Object)4);
                                                        response.put((Object)"errorDescription", (Object)"Incorrect \"deadline\"");
                                                    }
                                                }
                                                catch (Exception e2) {
                                                    response.put((Object)"errorCode", (Object)4);
                                                    response.put((Object)"errorDescription", (Object)"Incorrect \"fee\"");
                                                }
                                            }
                                        }
                                        catch (Exception e3) {
                                            response.put((Object)"errorCode", (Object)4);
                                            response.put((Object)"errorDescription", (Object)"Incorrect \"message\"");
                                        }
                                    }
                                    catch (Exception e7) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"recipient\"");
                                    }
                                }
                                break;
                            }
                            case "sendMoney": {
                                final String secretPhrase = req.getParameter("secretPhrase");
                                final String recipientValue = req.getParameter("recipient");
                                final String amountValue = req.getParameter("amount");
                                final String feeValue = req.getParameter("fee");
                                final String deadlineValue = req.getParameter("deadline");
                                final String referencedTransactionValue = req.getParameter("referencedTransaction");
                                if (secretPhrase == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"secretPhrase\" not specified");
                                }
                                else if (recipientValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"recipient\" not specified");
                                }
                                else if (amountValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"amount\" not specified");
                                }
                                else if (feeValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"fee\" not specified");
                                }
                                else if (deadlineValue == null) {
                                    response.put((Object)"errorCode", (Object)3);
                                    response.put((Object)"errorDescription", (Object)"\"deadline\" not specified");
                                }
                                else {
                                    try {
                                        final long recipient = parseUnsignedLong(recipientValue);
                                        try {
                                            final int amount = Integer.parseInt(amountValue);
                                            if (amount <= 0 || amount >= 1000000000L) {
                                                throw new Exception();
                                            }
                                            try {
                                                final int fee2 = Integer.parseInt(feeValue);
                                                if (fee2 <= 0 || fee2 >= 1000000000L) {
                                                    throw new Exception();
                                                }
                                                try {
                                                    final short deadline2 = Short.parseShort(deadlineValue);
                                                    if (deadline2 < 1) {
                                                        throw new Exception();
                                                    }
                                                    final long referencedTransaction2 = (referencedTransactionValue == null) ? 0L : parseUnsignedLong(referencedTransactionValue);
                                                    final byte[] publicKey4 = Crypto.getPublicKey(secretPhrase);
                                                    final Account account4 = Nxt.accounts.get(Account.getId(publicKey4));
                                                    if (account4 == null) {
                                                        response.put((Object)"errorCode", (Object)6);
                                                        response.put((Object)"errorDescription", (Object)"Not enough funds");
                                                    }
                                                    else if ((amount + fee2) * 100L > account4.getUnconfirmedBalance()) {
                                                        response.put((Object)"errorCode", (Object)6);
                                                        response.put((Object)"errorDescription", (Object)"Not enough funds");
                                                    }
                                                    else {
                                                        final Transaction transaction6 = new Transaction((byte)0, (byte)0, getEpochTime(System.currentTimeMillis()), deadline2, publicKey4, recipient, amount, fee2, referencedTransaction2, new byte[64]);
                                                        transaction6.sign(secretPhrase);
                                                        final JSONObject peerRequest4 = new JSONObject();
                                                        peerRequest4.put((Object)"requestType", (Object)"processTransactions");
                                                        final JSONArray transactionsData4 = new JSONArray();
                                                        transactionsData4.add((Object)transaction6.getJSONObject());
                                                        peerRequest4.put((Object)"transactions", (Object)transactionsData4);
                                                        Peer.sendToSomePeers(peerRequest4);
                                                        response.put((Object)"transaction", (Object)transaction6.getStringId());
                                                        response.put((Object)"bytes", (Object)convert(transaction6.getBytes()));
                                                        Nxt.nonBroadcastedTransactions.put(transaction6.id, transaction6);
                                                    }
                                                }
                                                catch (Exception e10) {
                                                    response.put((Object)"errorCode", (Object)4);
                                                    response.put((Object)"errorDescription", (Object)"Incorrect \"deadline\"");
                                                }
                                            }
                                            catch (Exception e2) {
                                                response.put((Object)"errorCode", (Object)4);
                                                response.put((Object)"errorDescription", (Object)"Incorrect \"fee\"");
                                            }
                                        }
                                        catch (Exception e3) {
                                            response.put((Object)"errorCode", (Object)4);
                                            response.put((Object)"errorDescription", (Object)"Incorrect \"amount\"");
                                        }
                                    }
                                    catch (Exception e7) {
                                        response.put((Object)"errorCode", (Object)4);
                                        response.put((Object)"errorDescription", (Object)"Incorrect \"recipient\"");
                                    }
                                }
                                break;
                            }
                            default: {
                                response.put((Object)"errorCode", (Object)1);
                                response.put((Object)"errorDescription", (Object)"Incorrect request");
                                break;
                            }
                        }
                    }
                }
                resp.setContentType("text/plain; charset=UTF-8");
                try (final Writer writer = resp.getWriter()) {
                    response.writeJSONString(writer);
                }
                return;
            }
            if (Nxt.allowedUserHosts != null && !Nxt.allowedUserHosts.contains(req.getRemoteHost())) {
                final JSONObject response = new JSONObject();
                response.put((Object)"response", (Object)"denyAccess");
                final JSONArray responses = new JSONArray();
                responses.add((Object)response);
                final JSONObject combinedResponse = new JSONObject();
                combinedResponse.put((Object)"responses", (Object)responses);
                resp.setContentType("text/plain; charset=UTF-8");
                try (final Writer writer2 = resp.getWriter()) {
                    combinedResponse.writeJSONString(writer2);
                }
                return;
            }
            user = Nxt.users.get(userPasscode);
            if (user == null) {
                user = new User();
                final User oldUser = Nxt.users.putIfAbsent(userPasscode, user);
                if (oldUser != null) {
                    user = oldUser;
                    user.isInactive = false;
                }
            }
            else {
                user.isInactive = false;
            }
            final String parameter = req.getParameter("requestType");
            switch (parameter) {
                case "generateAuthorizationToken": {
                    final String secretPhrase2 = req.getParameter("secretPhrase");
                    if (!user.secretPhrase.equals(secretPhrase2)) {
                        final JSONObject response2 = new JSONObject();
                        response2.put((Object)"response", (Object)"showMessage");
                        response2.put((Object)"message", (Object)"Invalid secret phrase!");
                        user.pendingResponses.offer(response2);
                        break;
                    }
                    final byte[] website2 = req.getParameter("website").trim().getBytes("UTF-8");
                    final byte[] data3 = new byte[website2.length + 32 + 4];
                    System.arraycopy(website2, 0, data3, 0, website2.length);
                    System.arraycopy(user.publicKey, 0, data3, website2.length, 32);
                    final int timestamp4 = getEpochTime(System.currentTimeMillis());
                    data3[website2.length + 32] = (byte)timestamp4;
                    data3[website2.length + 32 + 1] = (byte)(timestamp4 >> 8);
                    data3[website2.length + 32 + 2] = (byte)(timestamp4 >> 16);
                    data3[website2.length + 32 + 3] = (byte)(timestamp4 >> 24);
                    final byte[] token2 = new byte[100];
                    System.arraycopy(data3, website2.length, token2, 0, 36);
                    System.arraycopy(Crypto.sign(data3, user.secretPhrase), 0, token2, 36, 64);
                    String tokenString = "";
                    for (int ptr = 0; ptr < 100; ptr += 5) {
                        final long number2 = (token2[ptr] & 0xFF) | (token2[ptr + 1] & 0xFF) << 8 | (token2[ptr + 2] & 0xFF) << 16 | (token2[ptr + 3] & 0xFF) << 24 | (token2[ptr + 4] & 0xFF) << 32;
                        if (number2 < 32L) {
                            tokenString += "0000000";
                        }
                        else if (number2 < 1024L) {
                            tokenString += "000000";
                        }
                        else if (number2 < 32768L) {
                            tokenString += "00000";
                        }
                        else if (number2 < 1048576L) {
                            tokenString += "0000";
                        }
                        else if (number2 < 33554432L) {
                            tokenString += "000";
                        }
                        else if (number2 < 1073741824L) {
                            tokenString += "00";
                        }
                        else if (number2 < 34359738368L) {
                            tokenString += "0";
                        }
                        tokenString += Long.toString(number2, 32);
                    }
                    final JSONObject response3 = new JSONObject();
                    response3.put((Object)"response", (Object)"showAuthorizationToken");
                    response3.put((Object)"token", (Object)tokenString);
                    user.pendingResponses.offer(response3);
                    break;
                }
                case "getInitialData": {
                    final JSONArray unconfirmedTransactions = new JSONArray();
                    final JSONArray activePeers = new JSONArray();
                    final JSONArray knownPeers = new JSONArray();
                    final JSONArray blacklistedPeers = new JSONArray();
                    final JSONArray recentBlocks = new JSONArray();
                    for (final Transaction transaction7 : Nxt.unconfirmedTransactions.values()) {
                        final JSONObject unconfirmedTransaction = new JSONObject();
                        unconfirmedTransaction.put((Object)"index", (Object)transaction7.index);
                        unconfirmedTransaction.put((Object)"timestamp", (Object)transaction7.timestamp);
                        unconfirmedTransaction.put((Object)"deadline", (Object)transaction7.deadline);
                        unconfirmedTransaction.put((Object)"recipient", (Object)convert(transaction7.recipient));
                        unconfirmedTransaction.put((Object)"amount", (Object)transaction7.amount);
                        unconfirmedTransaction.put((Object)"fee", (Object)transaction7.fee);
                        unconfirmedTransaction.put((Object)"sender", (Object)convert(transaction7.getSenderAccountId()));
                        unconfirmedTransactions.add((Object)unconfirmedTransaction);
                    }
                    for (final Map.Entry<String, Peer> peerEntry : Nxt.peers.entrySet()) {
                        final String address = peerEntry.getKey();
                        final Peer peer2 = peerEntry.getValue();
                        if (peer2.blacklistingTime > 0L) {
                            final JSONObject blacklistedPeer = new JSONObject();
                            blacklistedPeer.put((Object)"index", (Object)peer2.index);
                            blacklistedPeer.put((Object)"announcedAddress", (Object)((peer2.announcedAddress.length() > 0) ? ((peer2.announcedAddress.length() > 30) ? (peer2.announcedAddress.substring(0, 30) + "...") : peer2.announcedAddress) : address));
                            for (final String wellKnownPeer : Nxt.wellKnownPeers) {
                                if (peer2.announcedAddress.equals(wellKnownPeer)) {
                                    blacklistedPeer.put((Object)"wellKnown", (Object)true);
                                    break;
                                }
                            }
                            blacklistedPeers.add((Object)blacklistedPeer);
                        }
                        else if (peer2.state == 0) {
                            if (peer2.announcedAddress.length() <= 0) {
                                continue;
                            }
                            final JSONObject knownPeer = new JSONObject();
                            knownPeer.put((Object)"index", (Object)peer2.index);
                            knownPeer.put((Object)"announcedAddress", (Object)((peer2.announcedAddress.length() > 30) ? (peer2.announcedAddress.substring(0, 30) + "...") : peer2.announcedAddress));
                            for (final String wellKnownPeer : Nxt.wellKnownPeers) {
                                if (peer2.announcedAddress.equals(wellKnownPeer)) {
                                    knownPeer.put((Object)"wellKnown", (Object)true);
                                    break;
                                }
                            }
                            knownPeers.add((Object)knownPeer);
                        }
                        else {
                            final JSONObject activePeer = new JSONObject();
                            activePeer.put((Object)"index", (Object)peer2.index);
                            if (peer2.state == 2) {
                                activePeer.put((Object)"disconnected", (Object)true);
                            }
                            activePeer.put((Object)"address", (Object)((address.length() > 30) ? (address.substring(0, 30) + "...") : address));
                            activePeer.put((Object)"announcedAddress", (Object)((peer2.announcedAddress.length() > 30) ? (peer2.announcedAddress.substring(0, 30) + "...") : peer2.announcedAddress));
                            activePeer.put((Object)"weight", (Object)peer2.getWeight());
                            activePeer.put((Object)"downloaded", (Object)peer2.downloadedVolume);
                            activePeer.put((Object)"uploaded", (Object)peer2.uploadedVolume);
                            activePeer.put((Object)"software", (Object)peer2.getSoftware());
                            for (final String wellKnownPeer : Nxt.wellKnownPeers) {
                                if (peer2.announcedAddress.equals(wellKnownPeer)) {
                                    activePeer.put((Object)"wellKnown", (Object)true);
                                    break;
                                }
                            }
                            activePeers.add((Object)activePeer);
                        }
                    }
                    long blockId = Nxt.lastBlock.get().getId();
                    int numberOfBlocks = 0;
                    while (numberOfBlocks < 60) {
                        ++numberOfBlocks;
                        final Block block4 = Nxt.blocks.get(blockId);
                        final JSONObject recentBlock = new JSONObject();
                        recentBlock.put((Object)"index", (Object)block4.index);
                        recentBlock.put((Object)"timestamp", (Object)block4.timestamp);
                        recentBlock.put((Object)"numberOfTransactions", (Object)block4.transactions.length);
                        recentBlock.put((Object)"totalAmount", (Object)block4.totalAmount);
                        recentBlock.put((Object)"totalFee", (Object)block4.totalFee);
                        recentBlock.put((Object)"payloadLength", (Object)block4.payloadLength);
                        recentBlock.put((Object)"generator", (Object)convert(block4.getGeneratorAccountId()));
                        recentBlock.put((Object)"height", (Object)block4.height);
                        recentBlock.put((Object)"version", (Object)block4.version);
                        recentBlock.put((Object)"block", (Object)block4.getStringId());
                        recentBlock.put((Object)"baseTarget", (Object)BigInteger.valueOf(block4.baseTarget).multiply(BigInteger.valueOf(100000L)).divide(BigInteger.valueOf(153722867L)));
                        recentBlocks.add((Object)recentBlock);
                        if (blockId == 2680262203532249785L) {
                            break;
                        }
                        blockId = block4.previousBlock;
                    }
                    final JSONObject response4 = new JSONObject();
                    response4.put((Object)"response", (Object)"processInitialData");
                    response4.put((Object)"version", (Object)"0.5.10");
                    if (unconfirmedTransactions.size() > 0) {
                        response4.put((Object)"unconfirmedTransactions", (Object)unconfirmedTransactions);
                    }
                    if (activePeers.size() > 0) {
                        response4.put((Object)"activePeers", (Object)activePeers);
                    }
                    if (knownPeers.size() > 0) {
                        response4.put((Object)"knownPeers", (Object)knownPeers);
                    }
                    if (blacklistedPeers.size() > 0) {
                        response4.put((Object)"blacklistedPeers", (Object)blacklistedPeers);
                    }
                    if (recentBlocks.size() > 0) {
                        response4.put((Object)"recentBlocks", (Object)recentBlocks);
                    }
                    user.pendingResponses.offer(response4);
                    break;
                }
                case "getNewData": {
                    break;
                }
                case "lockAccount": {
                    user.deinitializeKeyPair();
                    final JSONObject response5 = new JSONObject();
                    response5.put((Object)"response", (Object)"lockAccount");
                    user.pendingResponses.offer(response5);
                    break;
                }
                case "removeActivePeer": {
                    if (Nxt.allowedUserHosts == null && !InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
                        final JSONObject response5 = new JSONObject();
                        response5.put((Object)"response", (Object)"showMessage");
                        response5.put((Object)"message", (Object)"This operation is allowed to local host users only!");
                        user.pendingResponses.offer(response5);
                        break;
                    }
                    final int index = Integer.parseInt(req.getParameter("peer"));
                    for (final Peer peer3 : Nxt.peers.values()) {
                        if (peer3.index == index) {
                            if (peer3.blacklistingTime == 0L && peer3.state != 0) {
                                peer3.deactivate();
                                break;
                            }
                            break;
                        }
                    }
                    break;
                }
                case "removeBlacklistedPeer": {
                    if (Nxt.allowedUserHosts == null && !InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
                        final JSONObject response5 = new JSONObject();
                        response5.put((Object)"response", (Object)"showMessage");
                        response5.put((Object)"message", (Object)"This operation is allowed to local host users only!");
                        user.pendingResponses.offer(response5);
                        break;
                    }
                    final int index = Integer.parseInt(req.getParameter("peer"));
                    for (final Peer peer3 : Nxt.peers.values()) {
                        if (peer3.index == index) {
                            if (peer3.blacklistingTime > 0L) {
                                peer3.removeBlacklistedStatus();
                                break;
                            }
                            break;
                        }
                    }
                    break;
                }
                case "removeKnownPeer": {
                    if (Nxt.allowedUserHosts == null && !InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
                        final JSONObject response5 = new JSONObject();
                        response5.put((Object)"response", (Object)"showMessage");
                        response5.put((Object)"message", (Object)"This operation is allowed to local host users only!");
                        user.pendingResponses.offer(response5);
                        break;
                    }
                    final int index = Integer.parseInt(req.getParameter("peer"));
                    for (final Peer peer3 : Nxt.peers.values()) {
                        if (peer3.index == index) {
                            peer3.removePeer();
                            break;
                        }
                    }
                    break;
                }
                case "sendMoney": {
                    if (user.secretPhrase != null) {
                        final String recipientValue2 = req.getParameter("recipient");
                        final String amountValue2 = req.getParameter("amount");
                        final String feeValue2 = req.getParameter("fee");
                        final String deadlineValue2 = req.getParameter("deadline");
                        final String secretPhrase3 = req.getParameter("secretPhrase");
                        int amount2 = 0;
                        int fee3 = 0;
                        short deadline3 = 0;
                        long recipient2;
                        try {
                            recipient2 = parseUnsignedLong(recipientValue2);
                            amount2 = Integer.parseInt(amountValue2.trim());
                            fee3 = Integer.parseInt(feeValue2.trim());
                            deadline3 = (short)(Double.parseDouble(deadlineValue2) * 60.0);
                        }
                        catch (Exception e3) {
                            final JSONObject response6 = new JSONObject();
                            response6.put((Object)"response", (Object)"notifyOfIncorrectTransaction");
                            response6.put((Object)"message", (Object)"One of the fields is filled incorrectly!");
                            response6.put((Object)"recipient", (Object)recipientValue2);
                            response6.put((Object)"amount", (Object)amountValue2);
                            response6.put((Object)"fee", (Object)feeValue2);
                            response6.put((Object)"deadline", (Object)deadlineValue2);
                            user.pendingResponses.offer(response6);
                            break;
                        }
                        if (!user.secretPhrase.equals(secretPhrase3)) {
                            final JSONObject response7 = new JSONObject();
                            response7.put((Object)"response", (Object)"notifyOfIncorrectTransaction");
                            response7.put((Object)"message", (Object)"Wrong secret phrase!");
                            response7.put((Object)"recipient", (Object)recipientValue2);
                            response7.put((Object)"amount", (Object)amountValue2);
                            response7.put((Object)"fee", (Object)feeValue2);
                            response7.put((Object)"deadline", (Object)deadlineValue2);
                            user.pendingResponses.offer(response7);
                        }
                        else if (amount2 <= 0 || amount2 > 1000000000L) {
                            final JSONObject response7 = new JSONObject();
                            response7.put((Object)"response", (Object)"notifyOfIncorrectTransaction");
                            response7.put((Object)"message", (Object)"\"Amount\" must be greater than 0!");
                            response7.put((Object)"recipient", (Object)recipientValue2);
                            response7.put((Object)"amount", (Object)amountValue2);
                            response7.put((Object)"fee", (Object)feeValue2);
                            response7.put((Object)"deadline", (Object)deadlineValue2);
                            user.pendingResponses.offer(response7);
                        }
                        else if (fee3 <= 0 || fee3 > 1000000000L) {
                            final JSONObject response7 = new JSONObject();
                            response7.put((Object)"response", (Object)"notifyOfIncorrectTransaction");
                            response7.put((Object)"message", (Object)"\"Fee\" must be greater than 0!");
                            response7.put((Object)"recipient", (Object)recipientValue2);
                            response7.put((Object)"amount", (Object)amountValue2);
                            response7.put((Object)"fee", (Object)feeValue2);
                            response7.put((Object)"deadline", (Object)deadlineValue2);
                            user.pendingResponses.offer(response7);
                        }
                        else if (deadline3 < 1) {
                            final JSONObject response7 = new JSONObject();
                            response7.put((Object)"response", (Object)"notifyOfIncorrectTransaction");
                            response7.put((Object)"message", (Object)"\"Deadline\" must be greater or equal to 1 minute!");
                            response7.put((Object)"recipient", (Object)recipientValue2);
                            response7.put((Object)"amount", (Object)amountValue2);
                            response7.put((Object)"fee", (Object)feeValue2);
                            response7.put((Object)"deadline", (Object)deadlineValue2);
                            user.pendingResponses.offer(response7);
                        }
                        else {
                            final Account account5 = Nxt.accounts.get(Account.getId(user.publicKey));
                            if (account5 == null || (amount2 + fee3) * 100L > account5.getUnconfirmedBalance()) {
                                final JSONObject response6 = new JSONObject();
                                response6.put((Object)"response", (Object)"notifyOfIncorrectTransaction");
                                response6.put((Object)"message", (Object)"Not enough funds!");
                                response6.put((Object)"recipient", (Object)recipientValue2);
                                response6.put((Object)"amount", (Object)amountValue2);
                                response6.put((Object)"fee", (Object)feeValue2);
                                response6.put((Object)"deadline", (Object)deadlineValue2);
                                user.pendingResponses.offer(response6);
                            }
                            else {
                                final Transaction transaction3 = new Transaction((byte)0, (byte)0, getEpochTime(System.currentTimeMillis()), deadline3, user.publicKey, recipient2, amount2, fee3, 0L, new byte[64]);
                                transaction3.sign(user.secretPhrase);
                                final JSONObject peerRequest5 = new JSONObject();
                                peerRequest5.put((Object)"requestType", (Object)"processTransactions");
                                final JSONArray transactionsData5 = new JSONArray();
                                transactionsData5.add((Object)transaction3.getJSONObject());
                                peerRequest5.put((Object)"transactions", (Object)transactionsData5);
                                Peer.sendToSomePeers(peerRequest5);
                                final JSONObject response8 = new JSONObject();
                                response8.put((Object)"response", (Object)"notifyOfAcceptedTransaction");
                                user.pendingResponses.offer(response8);
                                Nxt.nonBroadcastedTransactions.put(transaction3.id, transaction3);
                            }
                        }
                        break;
                    }
                    break;
                }
                case "unlockAccount": {
                    final String secretPhrase2 = req.getParameter("secretPhrase");
                    for (final User u : Nxt.users.values()) {
                        if (secretPhrase2.equals(u.secretPhrase)) {
                            u.deinitializeKeyPair();
                            if (u.isInactive) {
                                continue;
                            }
                            final JSONObject response9 = new JSONObject();
                            response9.put((Object)"response", (Object)"lockAccount");
                            u.pendingResponses.offer(response9);
                        }
                    }
                    final BigInteger bigInt = user.initializeKeyPair(secretPhrase2);
                    final long accountId3 = bigInt.longValue();
                    final JSONObject response10 = new JSONObject();
                    response10.put((Object)"response", (Object)"unlockAccount");
                    response10.put((Object)"account", (Object)bigInt.toString());
                    if (secretPhrase2.length() < 30) {
                        response10.put((Object)"secretPhraseStrength", (Object)1);
                    }
                    else {
                        response10.put((Object)"secretPhraseStrength", (Object)5);
                    }
                    final Account account3 = Nxt.accounts.get(accountId3);
                    if (account3 == null) {
                        response10.put((Object)"balance", (Object)0);
                    }
                    else {
                        response10.put((Object)"balance", (Object)account3.getUnconfirmedBalance());
                        final long effectiveBalance = account3.getEffectiveBalance();
                        if (effectiveBalance > 0L) {
                            final JSONObject response11 = new JSONObject();
                            response11.put((Object)"response", (Object)"setBlockGenerationDeadline");
                            final Block lastBlock = Nxt.lastBlock.get();
                            final MessageDigest digest = getMessageDigest("SHA-256");
                            byte[] generationSignatureHash;
                            if (lastBlock.height < 30000) {
                                final byte[] generationSignature = Crypto.sign(lastBlock.generationSignature, user.secretPhrase);
                                generationSignatureHash = digest.digest(generationSignature);
                            }
                            else {
                                digest.update(lastBlock.generationSignature);
                                generationSignatureHash = digest.digest(user.publicKey);
                            }
                            final BigInteger hit = new BigInteger(1, new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0] });
                            response11.put((Object)"deadline", (Object)(hit.divide(BigInteger.valueOf(lastBlock.baseTarget).multiply(BigInteger.valueOf(effectiveBalance))).longValue() - (getEpochTime(System.currentTimeMillis()) - lastBlock.timestamp)));
                            user.pendingResponses.offer(response11);
                        }
                        final JSONArray myTransactions = new JSONArray();
                        final byte[] accountPublicKey2 = account3.publicKey.get();
                        for (final Transaction transaction3 : Nxt.unconfirmedTransactions.values()) {
                            if (Arrays.equals(transaction3.senderPublicKey, accountPublicKey2)) {
                                final JSONObject myTransaction = new JSONObject();
                                myTransaction.put((Object)"index", (Object)transaction3.index);
                                myTransaction.put((Object)"transactionTimestamp", (Object)transaction3.timestamp);
                                myTransaction.put((Object)"deadline", (Object)transaction3.deadline);
                                myTransaction.put((Object)"account", (Object)convert(transaction3.recipient));
                                myTransaction.put((Object)"sentAmount", (Object)transaction3.amount);
                                if (transaction3.recipient == accountId3) {
                                    myTransaction.put((Object)"receivedAmount", (Object)transaction3.amount);
                                }
                                myTransaction.put((Object)"fee", (Object)transaction3.fee);
                                myTransaction.put((Object)"numberOfConfirmations", (Object)0);
                                myTransaction.put((Object)"id", (Object)transaction3.getStringId());
                                myTransactions.add((Object)myTransaction);
                            }
                            else {
                                if (transaction3.recipient != accountId3) {
                                    continue;
                                }
                                final JSONObject myTransaction = new JSONObject();
                                myTransaction.put((Object)"index", (Object)transaction3.index);
                                myTransaction.put((Object)"transactionTimestamp", (Object)transaction3.timestamp);
                                myTransaction.put((Object)"deadline", (Object)transaction3.deadline);
                                myTransaction.put((Object)"account", (Object)convert(transaction3.getSenderAccountId()));
                                myTransaction.put((Object)"receivedAmount", (Object)transaction3.amount);
                                myTransaction.put((Object)"fee", (Object)transaction3.fee);
                                myTransaction.put((Object)"numberOfConfirmations", (Object)0);
                                myTransaction.put((Object)"id", (Object)transaction3.getStringId());
                                myTransactions.add((Object)myTransaction);
                            }
                        }
                        long blockId2 = Nxt.lastBlock.get().getId();
                        int numberOfConfirmations2 = 1;
                        while (myTransactions.size() < 1000) {
                            final Block block5 = Nxt.blocks.get(blockId2);
                            if (block5.totalFee > 0 && Arrays.equals(block5.generatorPublicKey, accountPublicKey2)) {
                                final JSONObject myTransaction2 = new JSONObject();
                                myTransaction2.put((Object)"index", (Object)block5.getStringId());
                                myTransaction2.put((Object)"blockTimestamp", (Object)block5.timestamp);
                                myTransaction2.put((Object)"block", (Object)block5.getStringId());
                                myTransaction2.put((Object)"earnedAmount", (Object)block5.totalFee);
                                myTransaction2.put((Object)"numberOfConfirmations", (Object)numberOfConfirmations2);
                                myTransaction2.put((Object)"id", (Object)"-");
                                myTransactions.add((Object)myTransaction2);
                            }
                            for (final Transaction transaction6 : block5.blockTransactions) {
                                if (Arrays.equals(transaction6.senderPublicKey, accountPublicKey2)) {
                                    final JSONObject myTransaction3 = new JSONObject();
                                    myTransaction3.put((Object)"index", (Object)transaction6.index);
                                    myTransaction3.put((Object)"blockTimestamp", (Object)block5.timestamp);
                                    myTransaction3.put((Object)"transactionTimestamp", (Object)transaction6.timestamp);
                                    myTransaction3.put((Object)"account", (Object)convert(transaction6.recipient));
                                    myTransaction3.put((Object)"sentAmount", (Object)transaction6.amount);
                                    if (transaction6.recipient == accountId3) {
                                        myTransaction3.put((Object)"receivedAmount", (Object)transaction6.amount);
                                    }
                                    myTransaction3.put((Object)"fee", (Object)transaction6.fee);
                                    myTransaction3.put((Object)"numberOfConfirmations", (Object)numberOfConfirmations2);
                                    myTransaction3.put((Object)"id", (Object)transaction6.getStringId());
                                    myTransactions.add((Object)myTransaction3);
                                }
                                else if (transaction6.recipient == accountId3) {
                                    final JSONObject myTransaction3 = new JSONObject();
                                    myTransaction3.put((Object)"index", (Object)transaction6.index);
                                    myTransaction3.put((Object)"blockTimestamp", (Object)block5.timestamp);
                                    myTransaction3.put((Object)"transactionTimestamp", (Object)transaction6.timestamp);
                                    myTransaction3.put((Object)"account", (Object)convert(transaction6.getSenderAccountId()));
                                    myTransaction3.put((Object)"receivedAmount", (Object)transaction6.amount);
                                    myTransaction3.put((Object)"fee", (Object)transaction6.fee);
                                    myTransaction3.put((Object)"numberOfConfirmations", (Object)numberOfConfirmations2);
                                    myTransaction3.put((Object)"id", (Object)transaction6.getStringId());
                                    myTransactions.add((Object)myTransaction3);
                                }
                            }
                            if (blockId2 == 2680262203532249785L) {
                                break;
                            }
                            blockId2 = block5.previousBlock;
                            ++numberOfConfirmations2;
                        }
                        if (myTransactions.size() > 0) {
                            final JSONObject response12 = new JSONObject();
                            response12.put((Object)"response", (Object)"processNewData");
                            response12.put((Object)"addedMyTransactions", (Object)myTransactions);
                            user.pendingResponses.offer(response12);
                        }
                    }
                    user.pendingResponses.offer(response10);
                    break;
                }
                default: {
                    final JSONObject response5 = new JSONObject();
                    response5.put((Object)"response", (Object)"showMessage");
                    response5.put((Object)"message", (Object)"Incorrect request!");
                    user.pendingResponses.offer(response5);
                    break;
                }
            }
        }
        catch (Exception e) {
            if (user != null) {
                logMessage("Error processing GET request", e);
                final JSONObject response = new JSONObject();
                response.put((Object)"response", (Object)"showMessage");
                response.put((Object)"message", (Object)e.toString());
                user.pendingResponses.offer(response);
            }
            else {
                logDebugMessage("Error processing GET request", e);
            }
        }
        if (user != null) {
            synchronized (user) {
                final JSONArray responses2 = new JSONArray();
                JSONObject pendingResponse;
                while ((pendingResponse = user.pendingResponses.poll()) != null) {
                    responses2.add((Object)pendingResponse);
                }
                if (responses2.size() > 0) {
                    final JSONObject combinedResponse = new JSONObject();
                    combinedResponse.put((Object)"responses", (Object)responses2);
                    if (user.asyncContext != null) {
                        user.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                        try (final Writer writer2 = user.asyncContext.getResponse().getWriter()) {
                            combinedResponse.writeJSONString(writer2);
                        }
                        user.asyncContext.complete();
                        (user.asyncContext = req.startAsync()).addListener((AsyncListener)new UserAsyncListener(user));
                        user.asyncContext.setTimeout(5000L);
                    }
                    else {
                        resp.setContentType("text/plain; charset=UTF-8");
                        try (final Writer writer2 = resp.getWriter()) {
                            combinedResponse.writeJSONString(writer2);
                        }
                    }
                }
                else {
                    if (user.asyncContext != null) {
                        user.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                        try (final Writer writer3 = user.asyncContext.getResponse().getWriter()) {
                            new JSONObject().writeJSONString(writer3);
                        }
                        user.asyncContext.complete();
                    }
                    (user.asyncContext = req.startAsync()).addListener((AsyncListener)new UserAsyncListener(user));
                    user.asyncContext.setTimeout(5000L);
                }
            }
        }
    }
    
    public void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        Peer peer = null;
        final JSONObject response = new JSONObject();
        try {
            final CountingInputStream cis = new CountingInputStream((InputStream)req.getInputStream());
            JSONObject request;
            try (final Reader reader = new BufferedReader(new InputStreamReader(cis, "UTF-8"))) {
                request = (JSONObject)JSONValue.parse(reader);
            }
            if (request == null) {
                return;
            }
            peer = Peer.addPeer(req.getRemoteHost(), "");
            if (peer != null) {
                if (peer.state == 2) {
                    peer.setState(1);
                }
                peer.updateDownloadedVolume(cis.getCount());
            }
            if (request.get((Object)"protocol") != null && ((Number)request.get((Object)"protocol")).intValue() == 1) {
                final String s = (String)request.get((Object)"requestType");
                switch (s) {
                    case "getCumulativeDifficulty": {
                        response.put((Object)"cumulativeDifficulty", (Object)Nxt.lastBlock.get().cumulativeDifficulty.toString());
                        break;
                    }
                    case "getInfo": {
                        if (peer != null) {
                            String announcedAddress = (String)request.get((Object)"announcedAddress");
                            if (announcedAddress != null) {
                                announcedAddress = announcedAddress.trim();
                                if (announcedAddress.length() > 0) {
                                    peer.announcedAddress = announcedAddress;
                                }
                            }
                            String application = (String)request.get((Object)"application");
                            if (application == null) {
                                application = "?";
                            }
                            else {
                                application = application.trim();
                                if (application.length() > 20) {
                                    application = application.substring(0, 20) + "...";
                                }
                            }
                            peer.application = application;
                            String version = (String)request.get((Object)"version");
                            if (version == null) {
                                version = "?";
                            }
                            else {
                                version = version.trim();
                                if (version.length() > 10) {
                                    version = version.substring(0, 10) + "...";
                                }
                            }
                            peer.version = version;
                            String platform = (String)request.get((Object)"platform");
                            if (platform == null) {
                                platform = "?";
                            }
                            else {
                                platform = platform.trim();
                                if (platform.length() > 10) {
                                    platform = platform.substring(0, 10) + "...";
                                }
                            }
                            peer.platform = platform;
                            peer.shareAddress = Boolean.TRUE.equals(request.get((Object)"shareAddress"));
                            if (peer.analyzeHallmark(req.getRemoteHost(), (String)request.get((Object)"hallmark"))) {
                                peer.setState(1);
                            }
                        }
                        if (Nxt.myHallmark != null && Nxt.myHallmark.length() > 0) {
                            response.put((Object)"hallmark", (Object)Nxt.myHallmark);
                        }
                        response.put((Object)"application", (Object)"NRS");
                        response.put((Object)"version", (Object)"0.5.10");
                        response.put((Object)"platform", (Object)Nxt.myPlatform);
                        response.put((Object)"shareAddress", (Object)Nxt.shareMyAddress);
                        break;
                    }
                    case "getMilestoneBlockIds": {
                        final JSONArray milestoneBlockIds = new JSONArray();
                        Block block = Nxt.lastBlock.get();
                        final int jumpLength = block.height * 4 / 1461 + 1;
                        while (block.height > 0) {
                            milestoneBlockIds.add((Object)block.getStringId());
                            for (int i = 0; i < jumpLength && block.height > 0; block = Nxt.blocks.get(block.previousBlock), ++i) {}
                        }
                        response.put((Object)"milestoneBlockIds", (Object)milestoneBlockIds);
                        break;
                    }
                    case "getNextBlockIds": {
                        final JSONArray nextBlockIds = new JSONArray();
                        Block block = Nxt.blocks.get(parseUnsignedLong((String)request.get((Object)"blockId")));
                        while (block != null && nextBlockIds.size() < 1440) {
                            block = Nxt.blocks.get(block.nextBlock);
                            if (block != null) {
                                nextBlockIds.add((Object)block.getStringId());
                            }
                        }
                        response.put((Object)"nextBlockIds", (Object)nextBlockIds);
                        break;
                    }
                    case "getNextBlocks": {
                        final List<Block> nextBlocks = new ArrayList<Block>();
                        int totalLength = 0;
                        Block block2 = Nxt.blocks.get(parseUnsignedLong((String)request.get((Object)"blockId")));
                        while (block2 != null) {
                            block2 = Nxt.blocks.get(block2.nextBlock);
                            if (block2 != null) {
                                final int length = 224 + block2.payloadLength;
                                if (totalLength + length > 1048576) {
                                    break;
                                }
                                nextBlocks.add(block2);
                                totalLength += length;
                            }
                        }
                        final JSONArray nextBlocksArray = new JSONArray();
                        for (final Block nextBlock : nextBlocks) {
                            nextBlocksArray.add((Object)nextBlock.getJSONStreamAware());
                        }
                        response.put((Object)"nextBlocks", (Object)nextBlocksArray);
                        break;
                    }
                    case "getPeers": {
                        final JSONArray peers = new JSONArray();
                        for (final Peer otherPeer : Nxt.peers.values()) {
                            if (otherPeer.blacklistingTime == 0L && otherPeer.announcedAddress.length() > 0 && otherPeer.state == 1 && otherPeer.shareAddress) {
                                peers.add((Object)otherPeer.announcedAddress);
                            }
                        }
                        response.put((Object)"peers", (Object)peers);
                        break;
                    }
                    case "getUnconfirmedTransactions": {
                        final JSONArray transactionsData = new JSONArray();
                        for (final Transaction transaction : Nxt.unconfirmedTransactions.values()) {
                            transactionsData.add((Object)transaction.getJSONObject());
                        }
                        response.put((Object)"unconfirmedTransactions", (Object)transactionsData);
                        break;
                    }
                    case "processBlock": {
                        final Block block = Block.getBlock(request);
                        boolean accepted;
                        if (block == null) {
                            accepted = false;
                            if (peer != null) {
                                peer.blacklist();
                            }
                        }
                        else {
                            final ByteBuffer buffer = ByteBuffer.allocate(224 + block.payloadLength);
                            buffer.order(ByteOrder.LITTLE_ENDIAN);
                            buffer.put(block.getBytes());
                            final JSONArray transactionsData2 = (JSONArray)request.get((Object)"transactions");
                            for (final Object transaction2 : transactionsData2) {
                                buffer.put(Transaction.getTransaction((JSONObject)transaction2).getBytes());
                            }
                            accepted = Block.pushBlock(buffer, true);
                        }
                        response.put((Object)"accepted", (Object)accepted);
                        break;
                    }
                    case "processTransactions": {
                        Transaction.processTransactions(request, "transactions");
                        break;
                    }
                    default: {
                        response.put((Object)"error", (Object)"Unsupported request type!");
                        break;
                    }
                }
            }
            else {
                logDebugMessage("Unsupported protocol " + request.get((Object)"protocol"));
                response.put((Object)"error", (Object)"Unsupported protocol!");
            }
        }
        catch (RuntimeException e) {
            logDebugMessage("Error processing POST request", e);
            response.put((Object)"error", (Object)e.toString());
        }
        resp.setContentType("text/plain; charset=UTF-8");
        final CountingOutputStream cos = new CountingOutputStream((OutputStream)resp.getOutputStream());
        try (final Writer writer = new BufferedWriter(new OutputStreamWriter(cos, "UTF-8"))) {
            response.writeJSONString(writer);
        }
        if (peer != null) {
            peer.updateUploadedVolume(cos.getCount());
        }
    }
    
    public void destroy() {
        shutdownExecutor(Nxt.scheduledThreadPool);
        shutdownExecutor(Nxt.sendToPeersService);
        try {
            Block.saveBlocks("blocks.nxt", true);
            logMessage("Saved blocks.nxt");
        }
        catch (RuntimeException e) {
            logMessage("Error saving blocks", e);
        }
        try {
            Transaction.saveTransactions("transactions.nxt");
            logMessage("Saved transactions.nxt");
        }
        catch (RuntimeException e) {
            logMessage("Error saving transactions", e);
        }
        logMessage("NRS 0.5.10 stopped.");
    }
    
    private static void shutdownExecutor(final ExecutorService executor) {
        executor.shutdown();
        try {
            executor.awaitTermination(10L, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!executor.isTerminated()) {
            logMessage("some threads didn't terminate, forcing shutdown");
            executor.shutdownNow();
        }
    }
    
    static {
        CREATOR_PUBLIC_KEY = new byte[] { 18, 89, -20, 33, -45, 26, 48, -119, -115, 124, -47, 96, -97, -128, -39, 102, -117, 71, 120, -29, -39, 126, -108, 16, 68, -77, -97, 12, 68, -46, -27, 27 };
        CHECKSUM_TRANSPARENT_FORGING = new byte[] { 27, -54, -59, -98, 49, -42, 48, -68, -112, 49, 41, 94, -41, 78, -84, 27, -87, -22, -28, 36, -34, -90, 112, -50, -9, 5, 89, -35, 80, -121, -128, 112 };
        two64 = new BigInteger("18446744073709551616");
        transactionCounter = new AtomicInteger();
        transactions = new ConcurrentHashMap<Long, Transaction>();
        unconfirmedTransactions = new ConcurrentHashMap<Long, Transaction>();
        doubleSpendingTransactions = new ConcurrentHashMap<Long, Transaction>();
        nonBroadcastedTransactions = new ConcurrentHashMap<Long, Transaction>();
        peerCounter = new AtomicInteger();
        peers = new ConcurrentHashMap<String, Peer>();
        blocksAndTransactionsLock = new Object();
        blockCounter = new AtomicInteger();
        blocks = new ConcurrentHashMap<Long, Block>();
        lastBlock = new AtomicReference<Block>();
        accounts = new ConcurrentHashMap<Long, Account>();
        aliases = new ConcurrentHashMap<String, Alias>();
        aliasIdToAliasMappings = new ConcurrentHashMap<Long, Alias>();
        assets = new ConcurrentHashMap<Long, Asset>();
        assetNameToIdMappings = new ConcurrentHashMap<String, Long>();
        askOrders = new ConcurrentHashMap<Long, AskOrder>();
        bidOrders = new ConcurrentHashMap<Long, BidOrder>();
        sortedAskOrders = new ConcurrentHashMap<Long, TreeSet<AskOrder>>();
        sortedBidOrders = new ConcurrentHashMap<Long, TreeSet<BidOrder>>();
        users = new ConcurrentHashMap<String, User>();
        scheduledThreadPool = Executors.newScheduledThreadPool(8);
        sendToPeersService = Executors.newFixedThreadPool(10);
        logDateFormat = new ThreadLocal<SimpleDateFormat>() {
            @Override
            protected SimpleDateFormat initialValue() {
                return new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss.SSS] ");
            }
        };
        debug = (System.getProperty("nxt.debug") != null);
        enableStackTraces = (System.getProperty("nxt.enableStackTraces") != null);
    }
    
    static class Account
    {
        final long id;
        private long balance;
        final int height;
        final AtomicReference<byte[]> publicKey;
        private final Map<Long, Integer> assetBalances;
        private long unconfirmedBalance;
        private final Map<Long, Integer> unconfirmedAssetBalances;
        
        private Account(final long id) {
            super();
            this.publicKey = new AtomicReference<byte[]>();
            this.assetBalances = new HashMap<Long, Integer>();
            this.unconfirmedAssetBalances = new HashMap<Long, Integer>();
            this.id = id;
            this.height = Nxt.lastBlock.get().height;
        }
        
        static Account addAccount(final long id) {
            final Account account = new Account(id);
            Nxt.accounts.put(id, account);
            return account;
        }
        
        boolean setOrVerify(final byte[] key) {
            return this.publicKey.compareAndSet(null, key) || Arrays.equals(key, this.publicKey.get());
        }
        
        void generateBlock(final String secretPhrase) {
            final Set<Transaction> sortedTransactions = new TreeSet<Transaction>();
            for (final Transaction transaction : Nxt.unconfirmedTransactions.values()) {
                if (transaction.referencedTransaction == 0L || Nxt.transactions.get(transaction.referencedTransaction) != null) {
                    sortedTransactions.add(transaction);
                }
            }
            final Map<Long, Transaction> newTransactions = new HashMap<Long, Transaction>();
            final Set<String> newAliases = new HashSet<String>();
            final Map<Long, Long> accumulatedAmounts = new HashMap<Long, Long>();
            int payloadLength = 0;
            while (payloadLength <= 32640) {
                final int prevNumberOfNewTransactions = newTransactions.size();
                for (final Transaction transaction2 : sortedTransactions) {
                    final int transactionLength = transaction2.getSize();
                    if (newTransactions.get(transaction2.getId()) == null && payloadLength + transactionLength <= 32640) {
                        final long sender = transaction2.getSenderAccountId();
                        Long accumulatedAmount = accumulatedAmounts.get(sender);
                        if (accumulatedAmount == null) {
                            accumulatedAmount = 0L;
                        }
                        final long amount = (transaction2.amount + transaction2.fee) * 100L;
                        if (accumulatedAmount + amount > Nxt.accounts.get(sender).getBalance() || !transaction2.validateAttachment()) {
                            continue;
                        }
                        Label_0359: {
                            switch (transaction2.type) {
                                case 1: {
                                    switch (transaction2.subtype) {
                                        case 1: {
                                            if (!newAliases.add(((Transaction.MessagingAliasAssignmentAttachment)transaction2.attachment).alias.toLowerCase())) {
                                                continue;
                                            }
                                            break Label_0359;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        accumulatedAmounts.put(sender, accumulatedAmount + amount);
                        newTransactions.put(transaction2.getId(), transaction2);
                        payloadLength += transactionLength;
                    }
                }
                if (newTransactions.size() == prevNumberOfNewTransactions) {
                    break;
                }
            }
            final Block previousBlock = Nxt.lastBlock.get();
            Block block;
            if (previousBlock.height < 30000) {
                block = new Block(1, Nxt.getEpochTime(System.currentTimeMillis()), previousBlock.getId(), newTransactions.size(), 0, 0, 0, null, Crypto.getPublicKey(secretPhrase), null, new byte[64]);
            }
            else {
                final byte[] previousBlockHash = Nxt.getMessageDigest("SHA-256").digest(previousBlock.getBytes());
                block = new Block(2, Nxt.getEpochTime(System.currentTimeMillis()), previousBlock.getId(), newTransactions.size(), 0, 0, 0, null, Crypto.getPublicKey(secretPhrase), null, new byte[64], previousBlockHash);
            }
            int i = 0;
            for (final Map.Entry<Long, Transaction> transactionEntry : newTransactions.entrySet()) {
                final Transaction transaction3 = transactionEntry.getValue();
                final Block block2 = block;
                block2.totalAmount += transaction3.amount;
                final Block block3 = block;
                block3.totalFee += transaction3.fee;
                final Block block4 = block;
                block4.payloadLength += transaction3.getSize();
                block.transactions[i++] = transactionEntry.getKey();
            }
            Arrays.sort(block.transactions);
            final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
            for (i = 0; i < block.transactions.length; ++i) {
                final Transaction transaction4 = newTransactions.get(block.transactions[i]);
                digest.update(transaction4.getBytes());
                block.blockTransactions[i] = transaction4;
            }
            block.payloadHash = digest.digest();
            if (previousBlock.height < 30000) {
                block.generationSignature = Crypto.sign(previousBlock.generationSignature, secretPhrase);
            }
            else {
                digest.update(previousBlock.generationSignature);
                block.generationSignature = digest.digest(Crypto.getPublicKey(secretPhrase));
            }
            final byte[] data = block.getBytes();
            final byte[] data2 = new byte[data.length - 64];
            System.arraycopy(data, 0, data2, 0, data2.length);
            block.blockSignature = Crypto.sign(data2, secretPhrase);
            if (block.verifyBlockSignature() && block.verifyGenerationSignature()) {
                final JSONObject request = block.getJSONObject();
                request.put((Object)"requestType", (Object)"processBlock");
                Peer.sendToSomePeers(request);
            }
            else {
                Nxt.logMessage("Generated an incorrect block. Waiting for the next one...");
            }
        }
        
        int getEffectiveBalance() {
            final Block lastBlock = Nxt.lastBlock.get();
            if (lastBlock.height >= 51000 || this.height >= 47000) {
                return (int)(this.getGuaranteedBalance(1440) / 100L);
            }
            if (this.height == 0) {
                return (int)(this.getBalance() / 100L);
            }
            if (lastBlock.height - this.height < 1440) {
                return 0;
            }
            int receivedInlastBlock = 0;
            for (final Transaction transaction : lastBlock.blockTransactions) {
                if (transaction.recipient == this.id) {
                    receivedInlastBlock += transaction.amount;
                }
            }
            return (int)(this.getBalance() / 100L) - receivedInlastBlock;
        }
        
        static long getId(final byte[] publicKey) {
            final byte[] publicKeyHash = Nxt.getMessageDigest("SHA-256").digest(publicKey);
            final BigInteger bigInteger = new BigInteger(1, new byte[] { publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4], publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0] });
            return bigInteger.longValue();
        }
        
        synchronized Integer getAssetBalance(final Long assetId) {
            return this.assetBalances.get(assetId);
        }
        
        synchronized Integer getUnconfirmedAssetBalance(final Long assetId) {
            return this.unconfirmedAssetBalances.get(assetId);
        }
        
        synchronized void addToAssetBalance(final Long assetId, final int quantity) {
            final Integer assetBalance = this.assetBalances.get(assetId);
            if (assetBalance == null) {
                this.assetBalances.put(assetId, quantity);
            }
            else {
                this.assetBalances.put(assetId, assetBalance + quantity);
            }
        }
        
        synchronized void addToUnconfirmedAssetBalance(final Long assetId, final int quantity) {
            final Integer unconfirmedAssetBalance = this.unconfirmedAssetBalances.get(assetId);
            if (unconfirmedAssetBalance == null) {
                this.unconfirmedAssetBalances.put(assetId, quantity);
            }
            else {
                this.unconfirmedAssetBalances.put(assetId, unconfirmedAssetBalance + quantity);
            }
        }
        
        synchronized void addToAssetAndUnconfirmedAssetBalance(final Long assetId, final int quantity) {
            final Integer assetBalance = this.assetBalances.get(assetId);
            if (assetBalance == null) {
                this.assetBalances.put(assetId, quantity);
                this.unconfirmedAssetBalances.put(assetId, quantity);
            }
            else {
                this.assetBalances.put(assetId, assetBalance + quantity);
                this.unconfirmedAssetBalances.put(assetId, this.unconfirmedAssetBalances.get(assetId) + quantity);
            }
        }
        
        synchronized long getBalance() {
            return this.balance;
        }
        
        long getGuaranteedBalance(final int numberOfConfirmations) {
            long guaranteedBalance = this.getBalance();
            final ArrayList<Block> lastBlocks = Block.getLastBlocks(numberOfConfirmations - 1);
            final byte[] accountPublicKey = this.publicKey.get();
            for (final Block block : lastBlocks) {
                if (Arrays.equals(block.generatorPublicKey, accountPublicKey) && (guaranteedBalance -= block.totalFee * 100L) <= 0L) {
                    return 0L;
                }
                int i = block.blockTransactions.length;
                while (i-- > 0) {
                    final Transaction transaction = block.blockTransactions[i];
                    if (Arrays.equals(transaction.senderPublicKey, accountPublicKey)) {
                        final long deltaBalance = transaction.getSenderDeltaBalance();
                        if (deltaBalance > 0L && (guaranteedBalance -= deltaBalance) <= 0L) {
                            return 0L;
                        }
                        if (deltaBalance < 0L && (guaranteedBalance += deltaBalance) <= 0L) {
                            return 0L;
                        }
                    }
                    if (transaction.recipient == this.id) {
                        final long deltaBalance = transaction.getRecipientDeltaBalance();
                        if (deltaBalance > 0L && (guaranteedBalance -= deltaBalance) <= 0L) {
                            return 0L;
                        }
                        if (deltaBalance < 0L && (guaranteedBalance += deltaBalance) <= 0L) {
                            return 0L;
                        }
                        continue;
                    }
                }
            }
            return guaranteedBalance;
        }
        
        synchronized long getUnconfirmedBalance() {
            return this.unconfirmedBalance;
        }
        
        void addToBalance(final long amount) {
            synchronized (this) {
                this.balance += amount;
            }
            this.updatePeerWeights();
        }
        
        void addToUnconfirmedBalance(final long amount) {
            synchronized (this) {
                this.unconfirmedBalance += amount;
            }
            this.updateUserUnconfirmedBalance();
        }
        
        void addToBalanceAndUnconfirmedBalance(final long amount) {
            synchronized (this) {
                this.balance += amount;
                this.unconfirmedBalance += amount;
            }
            this.updatePeerWeights();
            this.updateUserUnconfirmedBalance();
        }
        
        private void updatePeerWeights() {
            for (final Peer peer : Nxt.peers.values()) {
                if (peer.accountId == this.id && peer.adjustedWeight > 0L) {
                    peer.updateWeight();
                }
            }
        }
        
        private void updateUserUnconfirmedBalance() {
            final JSONObject response = new JSONObject();
            response.put((Object)"response", (Object)"setBalance");
            response.put((Object)"balance", (Object)this.getUnconfirmedBalance());
            final byte[] accountPublicKey = this.publicKey.get();
            for (final User user : Nxt.users.values()) {
                if (user.secretPhrase != null && Arrays.equals(user.publicKey, accountPublicKey)) {
                    user.send(response);
                }
            }
        }
    }
    
    static class Alias
    {
        final Account account;
        final long id;
        final String alias;
        volatile String uri;
        volatile int timestamp;
        
        Alias(final Account account, final long id, final String alias, final String uri, final int timestamp) {
            super();
            this.account = account;
            this.id = id;
            this.alias = alias;
            this.uri = uri;
            this.timestamp = timestamp;
        }
    }
    
    static class AskOrder implements Comparable<AskOrder>
    {
        final long id;
        final long height;
        final Account account;
        final long asset;
        volatile int quantity;
        final long price;
        
        AskOrder(final long id, final Account account, final long asset, final int quantity, final long price) {
            super();
            this.id = id;
            this.height = Nxt.lastBlock.get().height;
            this.account = account;
            this.asset = asset;
            this.quantity = quantity;
            this.price = price;
        }
        
        @Override
        public int compareTo(final AskOrder o) {
            if (this.price < o.price) {
                return -1;
            }
            if (this.price > o.price) {
                return 1;
            }
            if (this.height < o.height) {
                return -1;
            }
            if (this.height > o.height) {
                return 1;
            }
            if (this.id < o.id) {
                return -1;
            }
            if (this.id > o.id) {
                return 1;
            }
            return 0;
        }
    }
    
    static class Asset
    {
        final long accountId;
        final String name;
        final String description;
        final int quantity;
        
        Asset(final long accountId, final String name, final String description, final int quantity) {
            super();
            this.accountId = accountId;
            this.name = name;
            this.description = description;
            this.quantity = quantity;
        }
    }
    
    static class BidOrder implements Comparable<BidOrder>
    {
        final long id;
        final long height;
        final Account account;
        final long asset;
        volatile int quantity;
        final long price;
        
        BidOrder(final long id, final Account account, final long asset, final int quantity, final long price) {
            super();
            this.id = id;
            this.height = Nxt.lastBlock.get().height;
            this.account = account;
            this.asset = asset;
            this.quantity = quantity;
            this.price = price;
        }
        
        @Override
        public int compareTo(final BidOrder o) {
            if (this.price > o.price) {
                return -1;
            }
            if (this.price < o.price) {
                return 1;
            }
            if (this.height < o.height) {
                return -1;
            }
            if (this.height > o.height) {
                return 1;
            }
            if (this.id < o.id) {
                return -1;
            }
            if (this.id > o.id) {
                return 1;
            }
            return 0;
        }
    }
    
    static class Block implements Serializable
    {
        static final long serialVersionUID = 0L;
        static final long[] emptyLong;
        static final Transaction[] emptyTransactions;
        final int version;
        final int timestamp;
        final long previousBlock;
        int totalAmount;
        int totalFee;
        int payloadLength;
        byte[] payloadHash;
        final byte[] generatorPublicKey;
        byte[] generationSignature;
        byte[] blockSignature;
        final byte[] previousBlockHash;
        int index;
        final long[] transactions;
        long baseTarget;
        int height;
        volatile long nextBlock;
        BigInteger cumulativeDifficulty;
        transient Transaction[] blockTransactions;
        transient volatile long id;
        transient volatile String stringId;
        transient volatile long generatorAccountId;
        private transient SoftReference<JSONStreamAware> jsonRef;
        public static final Comparator<Block> heightComparator;
        
        Block(final int version, final int timestamp, final long previousBlock, final int numberOfTransactions, final int totalAmount, final int totalFee, final int payloadLength, final byte[] payloadHash, final byte[] generatorPublicKey, final byte[] generationSignature, final byte[] blockSignature) {
            this(version, timestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature, null);
        }
        
        Block(final int version, final int timestamp, final long previousBlock, final int numberOfTransactions, final int totalAmount, final int totalFee, final int payloadLength, final byte[] payloadHash, final byte[] generatorPublicKey, final byte[] generationSignature, final byte[] blockSignature, final byte[] previousBlockHash) {
            super();
            this.stringId = null;
            if (numberOfTransactions > 255 || numberOfTransactions < 0) {
                throw new IllegalArgumentException("attempted to create a block with " + numberOfTransactions + " transactions");
            }
            if (payloadLength > 32640 || payloadLength < 0) {
                throw new IllegalArgumentException("attempted to create a block with payloadLength " + payloadLength);
            }
            this.version = version;
            this.timestamp = timestamp;
            this.previousBlock = previousBlock;
            this.totalAmount = totalAmount;
            this.totalFee = totalFee;
            this.payloadLength = payloadLength;
            this.payloadHash = payloadHash;
            this.generatorPublicKey = generatorPublicKey;
            this.generationSignature = generationSignature;
            this.blockSignature = blockSignature;
            this.previousBlockHash = previousBlockHash;
            this.transactions = ((numberOfTransactions == 0) ? Block.emptyLong : new long[numberOfTransactions]);
            this.blockTransactions = ((numberOfTransactions == 0) ? Block.emptyTransactions : new Transaction[numberOfTransactions]);
        }
        
        private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            this.blockTransactions = ((this.transactions.length == 0) ? Block.emptyTransactions : new Transaction[this.transactions.length]);
        }
        
        void analyze() {
            synchronized (Nxt.blocksAndTransactionsLock) {
                for (int i = 0; i < this.transactions.length; ++i) {
                    this.blockTransactions[i] = Nxt.transactions.get(this.transactions[i]);
                    if (this.blockTransactions[i] == null) {
                        throw new IllegalStateException("Missing transaction " + Nxt.convert(this.transactions[i]));
                    }
                }
                if (this.previousBlock == 0L) {
                    this.baseTarget = 153722867L;
                    this.cumulativeDifficulty = BigInteger.ZERO;
                    Nxt.blocks.put(2680262203532249785L, this);
                    Nxt.lastBlock.set(this);
                    Account.addAccount(1739068987193023818L);
                }
                else {
                    final Block previousLastBlock = Nxt.lastBlock.get();
                    previousLastBlock.nextBlock = this.getId();
                    this.height = previousLastBlock.height + 1;
                    this.baseTarget = this.calculateBaseTarget();
                    this.cumulativeDifficulty = previousLastBlock.cumulativeDifficulty.add(Nxt.two64.divide(BigInteger.valueOf(this.baseTarget)));
                    if (previousLastBlock.getId() != this.previousBlock || !Nxt.lastBlock.compareAndSet(previousLastBlock, this)) {
                        throw new IllegalStateException("Last block not equal to this.previousBlock");
                    }
                    final Account generatorAccount = Nxt.accounts.get(this.getGeneratorAccountId());
                    generatorAccount.addToBalanceAndUnconfirmedBalance(this.totalFee * 100L);
                    if (Nxt.blocks.putIfAbsent(this.getId(), this) != null) {
                        throw new IllegalStateException("duplicate block id: " + this.getId());
                    }
                }
                for (final Transaction transaction : this.blockTransactions) {
                    transaction.height = this.height;
                    final long sender = transaction.getSenderAccountId();
                    final Account senderAccount = Nxt.accounts.get(sender);
                    if (!senderAccount.setOrVerify(transaction.senderPublicKey)) {
                        throw new RuntimeException("sender public key mismatch");
                    }
                    senderAccount.addToBalanceAndUnconfirmedBalance(-(transaction.amount + transaction.fee) * 100L);
                    Account recipientAccount = Nxt.accounts.get(transaction.recipient);
                    if (recipientAccount == null) {
                        recipientAccount = Account.addAccount(transaction.recipient);
                    }
                    Label_1298: {
                        switch (transaction.type) {
                            case 0: {
                                switch (transaction.subtype) {
                                    case 0: {
                                        recipientAccount.addToBalanceAndUnconfirmedBalance(transaction.amount * 100L);
                                        break;
                                    }
                                }
                                break;
                            }
                            case 1: {
                                switch (transaction.subtype) {
                                    case 1: {
                                        final Transaction.MessagingAliasAssignmentAttachment attachment = (Transaction.MessagingAliasAssignmentAttachment)transaction.attachment;
                                        final String normalizedAlias = attachment.alias.toLowerCase();
                                        Alias alias = Nxt.aliases.get(normalizedAlias);
                                        if (alias == null) {
                                            final long aliasId = transaction.getId();
                                            alias = new Alias(senderAccount, aliasId, attachment.alias, attachment.uri, this.timestamp);
                                            Nxt.aliases.put(normalizedAlias, alias);
                                            Nxt.aliasIdToAliasMappings.put(aliasId, alias);
                                            break;
                                        }
                                        alias.uri = attachment.uri;
                                        alias.timestamp = this.timestamp;
                                        break;
                                    }
                                }
                                break;
                            }
                            case 2: {
                                switch (transaction.subtype) {
                                    case 0: {
                                        final Transaction.ColoredCoinsAssetIssuanceAttachment attachment2 = (Transaction.ColoredCoinsAssetIssuanceAttachment)transaction.attachment;
                                        final long assetId = transaction.getId();
                                        final Asset asset = new Asset(sender, attachment2.name, attachment2.description, attachment2.quantity);
                                        Nxt.assets.put(assetId, asset);
                                        Nxt.assetNameToIdMappings.put(attachment2.name.toLowerCase(), assetId);
                                        Nxt.sortedAskOrders.put(assetId, new TreeSet<AskOrder>());
                                        Nxt.sortedBidOrders.put(assetId, new TreeSet<BidOrder>());
                                        senderAccount.addToAssetAndUnconfirmedAssetBalance(assetId, attachment2.quantity);
                                        break Label_1298;
                                    }
                                    case 1: {
                                        final Transaction.ColoredCoinsAssetTransferAttachment attachment3 = (Transaction.ColoredCoinsAssetTransferAttachment)transaction.attachment;
                                        senderAccount.addToAssetAndUnconfirmedAssetBalance(attachment3.asset, -attachment3.quantity);
                                        recipientAccount.addToAssetAndUnconfirmedAssetBalance(attachment3.asset, attachment3.quantity);
                                        break Label_1298;
                                    }
                                    case 2: {
                                        final Transaction.ColoredCoinsAskOrderPlacementAttachment attachment4 = (Transaction.ColoredCoinsAskOrderPlacementAttachment)transaction.attachment;
                                        final AskOrder order = new AskOrder(transaction.getId(), senderAccount, attachment4.asset, attachment4.quantity, attachment4.price);
                                        senderAccount.addToAssetAndUnconfirmedAssetBalance(attachment4.asset, -attachment4.quantity);
                                        Nxt.askOrders.put(order.id, order);
                                        Nxt.sortedAskOrders.get(attachment4.asset).add(order);
                                        Nxt.matchOrders(attachment4.asset);
                                        break Label_1298;
                                    }
                                    case 3: {
                                        final Transaction.ColoredCoinsBidOrderPlacementAttachment attachment5 = (Transaction.ColoredCoinsBidOrderPlacementAttachment)transaction.attachment;
                                        final BidOrder order2 = new BidOrder(transaction.getId(), senderAccount, attachment5.asset, attachment5.quantity, attachment5.price);
                                        senderAccount.addToBalanceAndUnconfirmedBalance(-attachment5.quantity * attachment5.price);
                                        Nxt.bidOrders.put(order2.id, order2);
                                        Nxt.sortedBidOrders.get(attachment5.asset).add(order2);
                                        Nxt.matchOrders(attachment5.asset);
                                        break Label_1298;
                                    }
                                    case 4: {
                                        final Transaction.ColoredCoinsAskOrderCancellationAttachment attachment6 = (Transaction.ColoredCoinsAskOrderCancellationAttachment)transaction.attachment;
                                        final AskOrder order = Nxt.askOrders.remove(attachment6.order);
                                        Nxt.sortedAskOrders.get(order.asset).remove(order);
                                        senderAccount.addToAssetAndUnconfirmedAssetBalance(order.asset, order.quantity);
                                        break Label_1298;
                                    }
                                    case 5: {
                                        final Transaction.ColoredCoinsBidOrderCancellationAttachment attachment7 = (Transaction.ColoredCoinsBidOrderCancellationAttachment)transaction.attachment;
                                        final BidOrder order2 = Nxt.bidOrders.remove(attachment7.order);
                                        Nxt.sortedBidOrders.get(order2.asset).remove(order2);
                                        senderAccount.addToBalanceAndUnconfirmedBalance(order2.quantity * order2.price);
                                        break Label_1298;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        private long calculateBaseTarget() {
            if (this.getId() == 2680262203532249785L) {
                return 153722867L;
            }
            final Block previousBlock = Nxt.blocks.get(this.previousBlock);
            final long curBaseTarget = previousBlock.baseTarget;
            long newBaseTarget = BigInteger.valueOf(curBaseTarget).multiply(BigInteger.valueOf(this.timestamp - previousBlock.timestamp)).divide(BigInteger.valueOf(60L)).longValue();
            if (newBaseTarget < 0L || newBaseTarget > 153722867000000000L) {
                newBaseTarget = 153722867000000000L;
            }
            if (newBaseTarget < curBaseTarget / 2L) {
                newBaseTarget = curBaseTarget / 2L;
            }
            if (newBaseTarget == 0L) {
                newBaseTarget = 1L;
            }
            long twofoldCurBaseTarget = curBaseTarget * 2L;
            if (twofoldCurBaseTarget < 0L) {
                twofoldCurBaseTarget = 153722867000000000L;
            }
            if (newBaseTarget > twofoldCurBaseTarget) {
                newBaseTarget = twofoldCurBaseTarget;
            }
            return newBaseTarget;
        }
        
        static Block getBlock(final JSONObject blockData) {
            try {
                final int version = (int)blockData.get((Object)"version");
                final int timestamp = (int)blockData.get((Object)"timestamp");
                final long previousBlock = Nxt.parseUnsignedLong((String)blockData.get((Object)"previousBlock"));
                final int numberOfTransactions = (int)blockData.get((Object)"numberOfTransactions");
                final int totalAmount = (int)blockData.get((Object)"totalAmount");
                final int totalFee = (int)blockData.get((Object)"totalFee");
                final int payloadLength = (int)blockData.get((Object)"payloadLength");
                final byte[] payloadHash = Nxt.convert((String)blockData.get((Object)"payloadHash"));
                final byte[] generatorPublicKey = Nxt.convert((String)blockData.get((Object)"generatorPublicKey"));
                final byte[] generationSignature = Nxt.convert((String)blockData.get((Object)"generationSignature"));
                final byte[] blockSignature = Nxt.convert((String)blockData.get((Object)"blockSignature"));
                final byte[] previousBlockHash = (byte[])((version == 1) ? null : Nxt.convert((String)blockData.get((Object)"previousBlockHash")));
                if (numberOfTransactions > 255 || payloadLength > 32640) {
                    return null;
                }
                return new Block(version, timestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature, previousBlockHash);
            }
            catch (RuntimeException e) {
                return null;
            }
        }
        
        byte[] getBytes() {
            final ByteBuffer buffer = ByteBuffer.allocate(224);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(this.version);
            buffer.putInt(this.timestamp);
            buffer.putLong(this.previousBlock);
            buffer.putInt(this.transactions.length);
            buffer.putInt(this.totalAmount);
            buffer.putInt(this.totalFee);
            buffer.putInt(this.payloadLength);
            buffer.put(this.payloadHash);
            buffer.put(this.generatorPublicKey);
            buffer.put(this.generationSignature);
            if (this.version > 1) {
                buffer.put(this.previousBlockHash);
            }
            buffer.put(this.blockSignature);
            return buffer.array();
        }
        
        long getId() {
            this.calculateIds();
            return this.id;
        }
        
        String getStringId() {
            this.calculateIds();
            return this.stringId;
        }
        
        long getGeneratorAccountId() {
            this.calculateIds();
            return this.generatorAccountId;
        }
        
        private void calculateIds() {
            if (this.stringId != null) {
                return;
            }
            final byte[] hash = Nxt.getMessageDigest("SHA-256").digest(this.getBytes());
            final BigInteger bigInteger = new BigInteger(1, new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
            this.id = bigInteger.longValue();
            this.stringId = bigInteger.toString();
            this.generatorAccountId = Account.getId(this.generatorPublicKey);
        }
        
        JSONObject getJSONObject() {
            final JSONObject block = new JSONObject();
            block.put((Object)"version", (Object)this.version);
            block.put((Object)"timestamp", (Object)this.timestamp);
            block.put((Object)"previousBlock", (Object)Nxt.convert(this.previousBlock));
            block.put((Object)"numberOfTransactions", (Object)this.transactions.length);
            block.put((Object)"totalAmount", (Object)this.totalAmount);
            block.put((Object)"totalFee", (Object)this.totalFee);
            block.put((Object)"payloadLength", (Object)this.payloadLength);
            block.put((Object)"payloadHash", (Object)Nxt.convert(this.payloadHash));
            block.put((Object)"generatorPublicKey", (Object)Nxt.convert(this.generatorPublicKey));
            block.put((Object)"generationSignature", (Object)Nxt.convert(this.generationSignature));
            if (this.version > 1) {
                block.put((Object)"previousBlockHash", (Object)Nxt.convert(this.previousBlockHash));
            }
            block.put((Object)"blockSignature", (Object)Nxt.convert(this.blockSignature));
            final JSONArray transactionsData = new JSONArray();
            for (final Transaction transaction : this.blockTransactions) {
                transactionsData.add((Object)transaction.getJSONObject());
            }
            block.put((Object)"transactions", (Object)transactionsData);
            return block;
        }
        
        synchronized JSONStreamAware getJSONStreamAware() {
            if (this.jsonRef != null) {
                final JSONStreamAware json = this.jsonRef.get();
                if (json != null) {
                    return json;
                }
            }
            final JSONStreamAware json = (JSONStreamAware)new JSONStreamAware() {
                private char[] jsonChars = Block.this.getJSONObject().toJSONString().toCharArray();
                
                public void writeJSONString(final Writer out) throws IOException {
                    out.write(this.jsonChars);
                }
            };
            this.jsonRef = new SoftReference<JSONStreamAware>(json);
            return json;
        }
        
        static ArrayList<Block> getLastBlocks(final int numberOfBlocks) {
            final ArrayList<Block> lastBlocks = new ArrayList<Block>(numberOfBlocks);
            long curBlock = Nxt.lastBlock.get().getId();
            do {
                final Block block = Nxt.blocks.get(curBlock);
                lastBlocks.add(block);
                curBlock = block.previousBlock;
            } while (lastBlocks.size() < numberOfBlocks && curBlock != 0L);
            return lastBlocks;
        }
        
        static void loadBlocks(final String fileName) throws FileNotFoundException {
            try (final FileInputStream fileInputStream = new FileInputStream(fileName);
                 final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                Nxt.blockCounter.set(objectInputStream.readInt());
                Nxt.blocks.clear();
                Nxt.blocks.putAll((Map<?, ?>)objectInputStream.readObject());
            }
            catch (FileNotFoundException e) {
                throw e;
            }
            catch (IOException | ClassNotFoundException e2) {
                Nxt.logMessage("Error loading blocks from " + fileName, e2);
                System.exit(1);
            }
        }
        
        static boolean popLastBlock() {
            try {
                final JSONObject response = new JSONObject();
                response.put((Object)"response", (Object)"processNewData");
                final JSONArray addedUnconfirmedTransactions = new JSONArray();
                final Block block;
                synchronized (Nxt.blocksAndTransactionsLock) {
                    block = Nxt.lastBlock.get();
                    if (block.getId() == 2680262203532249785L) {
                        return false;
                    }
                    final Block previousBlock = Nxt.blocks.get(block.previousBlock);
                    if (previousBlock == null) {
                        Nxt.logMessage("Previous block is null");
                        throw new IllegalStateException();
                    }
                    if (!Nxt.lastBlock.compareAndSet(block, previousBlock)) {
                        Nxt.logMessage("This block is no longer last block");
                        throw new IllegalStateException();
                    }
                    final Account generatorAccount = Nxt.accounts.get(block.getGeneratorAccountId());
                    generatorAccount.addToBalanceAndUnconfirmedBalance(-block.totalFee * 100L);
                    for (final long transactionId : block.transactions) {
                        final Transaction transaction = Nxt.transactions.remove(transactionId);
                        Nxt.unconfirmedTransactions.put(transactionId, transaction);
                        final Account senderAccount = Nxt.accounts.get(transaction.getSenderAccountId());
                        senderAccount.addToBalance((transaction.amount + transaction.fee) * 100L);
                        final Account recipientAccount = Nxt.accounts.get(transaction.recipient);
                        recipientAccount.addToBalanceAndUnconfirmedBalance(-transaction.amount * 100L);
                        final JSONObject addedUnconfirmedTransaction = new JSONObject();
                        addedUnconfirmedTransaction.put((Object)"index", (Object)transaction.index);
                        addedUnconfirmedTransaction.put((Object)"timestamp", (Object)transaction.timestamp);
                        addedUnconfirmedTransaction.put((Object)"deadline", (Object)transaction.deadline);
                        addedUnconfirmedTransaction.put((Object)"recipient", (Object)Nxt.convert(transaction.recipient));
                        addedUnconfirmedTransaction.put((Object)"amount", (Object)transaction.amount);
                        addedUnconfirmedTransaction.put((Object)"fee", (Object)transaction.fee);
                        addedUnconfirmedTransaction.put((Object)"sender", (Object)Nxt.convert(transaction.getSenderAccountId()));
                        addedUnconfirmedTransaction.put((Object)"id", (Object)transaction.getStringId());
                        addedUnconfirmedTransactions.add((Object)addedUnconfirmedTransaction);
                    }
                }
                final JSONArray addedOrphanedBlocks = new JSONArray();
                final JSONObject addedOrphanedBlock = new JSONObject();
                addedOrphanedBlock.put((Object)"index", (Object)block.index);
                addedOrphanedBlock.put((Object)"timestamp", (Object)block.timestamp);
                addedOrphanedBlock.put((Object)"numberOfTransactions", (Object)block.transactions.length);
                addedOrphanedBlock.put((Object)"totalAmount", (Object)block.totalAmount);
                addedOrphanedBlock.put((Object)"totalFee", (Object)block.totalFee);
                addedOrphanedBlock.put((Object)"payloadLength", (Object)block.payloadLength);
                addedOrphanedBlock.put((Object)"generator", (Object)Nxt.convert(block.getGeneratorAccountId()));
                addedOrphanedBlock.put((Object)"height", (Object)block.height);
                addedOrphanedBlock.put((Object)"version", (Object)block.version);
                addedOrphanedBlock.put((Object)"block", (Object)block.getStringId());
                addedOrphanedBlock.put((Object)"baseTarget", (Object)BigInteger.valueOf(block.baseTarget).multiply(BigInteger.valueOf(100000L)).divide(BigInteger.valueOf(153722867L)));
                addedOrphanedBlocks.add((Object)addedOrphanedBlock);
                response.put((Object)"addedOrphanedBlocks", (Object)addedOrphanedBlocks);
                if (addedUnconfirmedTransactions.size() > 0) {
                    response.put((Object)"addedUnconfirmedTransactions", (Object)addedUnconfirmedTransactions);
                }
                for (final User user : Nxt.users.values()) {
                    user.send(response);
                }
            }
            catch (RuntimeException e) {
                Nxt.logMessage("Error popping last block", e);
                return false;
            }
            return true;
        }
        
        static boolean pushBlock(final ByteBuffer buffer, final boolean savingFlag) {
            final int curTime = Nxt.getEpochTime(System.currentTimeMillis());
            Block block;
            JSONArray addedConfirmedTransactions;
            JSONArray removedUnconfirmedTransactions;
            synchronized (Nxt.blocksAndTransactionsLock) {
                try {
                    final Block previousLastBlock = Nxt.lastBlock.get();
                    buffer.flip();
                    final int version = buffer.getInt();
                    if (version != ((previousLastBlock.height < 30000) ? 1 : 2)) {
                        return false;
                    }
                    if (previousLastBlock.height == 30000) {
                        final byte[] checksum = Transaction.calculateTransactionsChecksum();
                        if (Nxt.CHECKSUM_TRANSPARENT_FORGING == null) {
                            System.out.println(Arrays.toString(checksum));
                        }
                        else {
                            if (!Arrays.equals(checksum, Nxt.CHECKSUM_TRANSPARENT_FORGING)) {
                                Nxt.logMessage("Checksum failed at block 30000");
                                return false;
                            }
                            Nxt.logMessage("Checksum passed at block 30000");
                        }
                    }
                    final int blockTimestamp = buffer.getInt();
                    final long previousBlock = buffer.getLong();
                    final int numberOfTransactions = buffer.getInt();
                    final int totalAmount = buffer.getInt();
                    final int totalFee = buffer.getInt();
                    final int payloadLength = buffer.getInt();
                    final byte[] payloadHash = new byte[32];
                    buffer.get(payloadHash);
                    final byte[] generatorPublicKey = new byte[32];
                    buffer.get(generatorPublicKey);
                    byte[] generationSignature;
                    byte[] previousBlockHash;
                    if (version == 1) {
                        generationSignature = new byte[64];
                        buffer.get(generationSignature);
                        previousBlockHash = null;
                    }
                    else {
                        generationSignature = new byte[32];
                        buffer.get(generationSignature);
                        previousBlockHash = new byte[32];
                        buffer.get(previousBlockHash);
                        if (!Arrays.equals(Nxt.getMessageDigest("SHA-256").digest(previousLastBlock.getBytes()), previousBlockHash)) {
                            return false;
                        }
                    }
                    final byte[] blockSignature = new byte[64];
                    buffer.get(blockSignature);
                    if (blockTimestamp > curTime + 15 || blockTimestamp <= previousLastBlock.timestamp) {
                        return false;
                    }
                    if (payloadLength > 32640 || 224 + payloadLength != buffer.capacity() || numberOfTransactions > 255) {
                        return false;
                    }
                    block = new Block(version, blockTimestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature, previousBlockHash);
                    if (block.transactions.length > 255 || block.previousBlock != previousLastBlock.getId() || block.getId() == 0L || Nxt.blocks.get(block.getId()) != null || !block.verifyGenerationSignature() || !block.verifyBlockSignature()) {
                        return false;
                    }
                    block.index = Nxt.blockCounter.incrementAndGet();
                    final HashMap<Long, Transaction> blockTransactions = new HashMap<Long, Transaction>();
                    final HashSet<String> blockAliases = new HashSet<String>();
                    for (int i = 0; i < block.transactions.length; ++i) {
                        final Transaction transaction = Transaction.getTransaction(buffer);
                        transaction.index = Nxt.transactionCounter.incrementAndGet();
                        final HashMap<Long, Transaction> hashMap = blockTransactions;
                        final long[] transactions = block.transactions;
                        final int n = i;
                        final long id = transaction.getId();
                        transactions[n] = id;
                        if (hashMap.put(id, transaction) != null) {
                            return false;
                        }
                        switch (transaction.type) {
                            case 1: {
                                switch (transaction.subtype) {
                                    case 1: {
                                        if (!blockAliases.add(((Transaction.MessagingAliasAssignmentAttachment)transaction.attachment).alias.toLowerCase())) {
                                            return false;
                                        }
                                        continue;
                                    }
                                }
                                break;
                            }
                        }
                    }
                    Arrays.sort(block.transactions);
                    final HashMap<Long, Long> accumulatedAmounts = new HashMap<Long, Long>();
                    final HashMap<Long, HashMap<Long, Long>> accumulatedAssetQuantities = new HashMap<Long, HashMap<Long, Long>>();
                    int calculatedTotalAmount = 0;
                    int calculatedTotalFee = 0;
                    final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                    int j;
                    for (j = 0; j < block.transactions.length; ++j) {
                        final Transaction transaction2 = blockTransactions.get(block.transactions[j]);
                        if (transaction2.timestamp > curTime + 15 || transaction2.deadline < 1 || (transaction2.timestamp + transaction2.deadline * 60 < blockTimestamp && previousLastBlock.height > 303) || transaction2.fee <= 0 || transaction2.fee > 1000000000L || transaction2.amount < 0 || transaction2.amount > 1000000000L || !transaction2.validateAttachment() || Nxt.transactions.get(block.transactions[j]) != null || (transaction2.referencedTransaction != 0L && Nxt.transactions.get(transaction2.referencedTransaction) == null && blockTransactions.get(transaction2.referencedTransaction) == null)) {
                            break;
                        }
                        if (Nxt.unconfirmedTransactions.get(block.transactions[j]) == null && !transaction2.verify()) {
                            break;
                        }
                        final long sender = transaction2.getSenderAccountId();
                        Long accumulatedAmount = accumulatedAmounts.get(sender);
                        if (accumulatedAmount == null) {
                            accumulatedAmount = 0L;
                        }
                        accumulatedAmounts.put(sender, accumulatedAmount + (transaction2.amount + transaction2.fee) * 100L);
                        if (transaction2.type == 0) {
                            if (transaction2.subtype != 0) {
                                break;
                            }
                            calculatedTotalAmount += transaction2.amount;
                        }
                        else if (transaction2.type == 1) {
                            if (transaction2.subtype != 0 && transaction2.subtype != 1) {
                                break;
                            }
                        }
                        else {
                            if (transaction2.type != 2) {
                                break;
                            }
                            if (transaction2.subtype == 1) {
                                final Transaction.ColoredCoinsAssetTransferAttachment attachment = (Transaction.ColoredCoinsAssetTransferAttachment)transaction2.attachment;
                                HashMap<Long, Long> accountAccumulatedAssetQuantities = accumulatedAssetQuantities.get(sender);
                                if (accountAccumulatedAssetQuantities == null) {
                                    accountAccumulatedAssetQuantities = new HashMap<Long, Long>();
                                    accumulatedAssetQuantities.put(sender, accountAccumulatedAssetQuantities);
                                }
                                Long assetAccumulatedAssetQuantities = accountAccumulatedAssetQuantities.get(attachment.asset);
                                if (assetAccumulatedAssetQuantities == null) {
                                    assetAccumulatedAssetQuantities = 0L;
                                }
                                accountAccumulatedAssetQuantities.put(attachment.asset, assetAccumulatedAssetQuantities + attachment.quantity);
                            }
                            else if (transaction2.subtype == 2) {
                                final Transaction.ColoredCoinsAskOrderPlacementAttachment attachment2 = (Transaction.ColoredCoinsAskOrderPlacementAttachment)transaction2.attachment;
                                HashMap<Long, Long> accountAccumulatedAssetQuantities = accumulatedAssetQuantities.get(sender);
                                if (accountAccumulatedAssetQuantities == null) {
                                    accountAccumulatedAssetQuantities = new HashMap<Long, Long>();
                                    accumulatedAssetQuantities.put(sender, accountAccumulatedAssetQuantities);
                                }
                                Long assetAccumulatedAssetQuantities = accountAccumulatedAssetQuantities.get(attachment2.asset);
                                if (assetAccumulatedAssetQuantities == null) {
                                    assetAccumulatedAssetQuantities = 0L;
                                }
                                accountAccumulatedAssetQuantities.put(attachment2.asset, assetAccumulatedAssetQuantities + attachment2.quantity);
                            }
                            else if (transaction2.subtype == 3) {
                                final Transaction.ColoredCoinsBidOrderPlacementAttachment attachment3 = (Transaction.ColoredCoinsBidOrderPlacementAttachment)transaction2.attachment;
                                accumulatedAmounts.put(sender, accumulatedAmount + attachment3.quantity * attachment3.price);
                            }
                            else if (transaction2.subtype != 0 && transaction2.subtype != 4 && transaction2.subtype != 5) {
                                break;
                            }
                        }
                        calculatedTotalFee += transaction2.fee;
                        digest.update(transaction2.getBytes());
                    }
                    if (j != block.transactions.length || calculatedTotalAmount != block.totalAmount || calculatedTotalFee != block.totalFee) {
                        return false;
                    }
                    if (!Arrays.equals(digest.digest(), block.payloadHash)) {
                        return false;
                    }
                    for (final Map.Entry<Long, Long> accumulatedAmountEntry : accumulatedAmounts.entrySet()) {
                        final Account senderAccount = Nxt.accounts.get(accumulatedAmountEntry.getKey());
                        if (senderAccount.getBalance() < accumulatedAmountEntry.getValue()) {
                            return false;
                        }
                    }
                    for (final Map.Entry<Long, HashMap<Long, Long>> accumulatedAssetQuantitiesEntry : accumulatedAssetQuantities.entrySet()) {
                        final Account senderAccount = Nxt.accounts.get(accumulatedAssetQuantitiesEntry.getKey());
                        for (final Map.Entry<Long, Long> accountAccumulatedAssetQuantitiesEntry : accumulatedAssetQuantitiesEntry.getValue().entrySet()) {
                            final long asset = accountAccumulatedAssetQuantitiesEntry.getKey();
                            final long quantity = accountAccumulatedAssetQuantitiesEntry.getValue();
                            if (senderAccount.getAssetBalance(asset) < quantity) {
                                return false;
                            }
                        }
                    }
                    block.height = previousLastBlock.height + 1;
                    for (final Map.Entry<Long, Transaction> transactionEntry : blockTransactions.entrySet()) {
                        final Transaction transaction3 = transactionEntry.getValue();
                        transaction3.height = block.height;
                        transaction3.block = block.getId();
                        if (Nxt.transactions.putIfAbsent(transactionEntry.getKey(), transaction3) != null) {
                            Nxt.logMessage("duplicate transaction id " + transactionEntry.getKey());
                            return false;
                        }
                    }
                    block.analyze();
                    addedConfirmedTransactions = new JSONArray();
                    removedUnconfirmedTransactions = new JSONArray();
                    for (final Map.Entry<Long, Transaction> transactionEntry : blockTransactions.entrySet()) {
                        final Transaction transaction3 = transactionEntry.getValue();
                        final JSONObject addedConfirmedTransaction = new JSONObject();
                        addedConfirmedTransaction.put((Object)"index", (Object)transaction3.index);
                        addedConfirmedTransaction.put((Object)"blockTimestamp", (Object)block.timestamp);
                        addedConfirmedTransaction.put((Object)"transactionTimestamp", (Object)transaction3.timestamp);
                        addedConfirmedTransaction.put((Object)"sender", (Object)Nxt.convert(transaction3.getSenderAccountId()));
                        addedConfirmedTransaction.put((Object)"recipient", (Object)Nxt.convert(transaction3.recipient));
                        addedConfirmedTransaction.put((Object)"amount", (Object)transaction3.amount);
                        addedConfirmedTransaction.put((Object)"fee", (Object)transaction3.fee);
                        addedConfirmedTransaction.put((Object)"id", (Object)transaction3.getStringId());
                        addedConfirmedTransactions.add((Object)addedConfirmedTransaction);
                        final Transaction removedTransaction = Nxt.unconfirmedTransactions.remove(transactionEntry.getKey());
                        if (removedTransaction != null) {
                            final JSONObject removedUnconfirmedTransaction = new JSONObject();
                            removedUnconfirmedTransaction.put((Object)"index", (Object)removedTransaction.index);
                            removedUnconfirmedTransactions.add((Object)removedUnconfirmedTransaction);
                            final Account senderAccount2 = Nxt.accounts.get(removedTransaction.getSenderAccountId());
                            senderAccount2.addToUnconfirmedBalance((removedTransaction.amount + removedTransaction.fee) * 100L);
                        }
                    }
                    if (savingFlag) {
                        Transaction.saveTransactions("transactions.nxt");
                        saveBlocks("blocks.nxt", false);
                    }
                }
                catch (RuntimeException e) {
                    Nxt.logMessage("Error pushing block", e);
                    return false;
                }
            }
            if (block.timestamp >= curTime - 15) {
                final JSONObject request = block.getJSONObject();
                request.put((Object)"requestType", (Object)"processBlock");
                Peer.sendToSomePeers(request);
            }
            final JSONArray addedRecentBlocks = new JSONArray();
            final JSONObject addedRecentBlock = new JSONObject();
            addedRecentBlock.put((Object)"index", (Object)block.index);
            addedRecentBlock.put((Object)"timestamp", (Object)block.timestamp);
            addedRecentBlock.put((Object)"numberOfTransactions", (Object)block.transactions.length);
            addedRecentBlock.put((Object)"totalAmount", (Object)block.totalAmount);
            addedRecentBlock.put((Object)"totalFee", (Object)block.totalFee);
            addedRecentBlock.put((Object)"payloadLength", (Object)block.payloadLength);
            addedRecentBlock.put((Object)"generator", (Object)Nxt.convert(block.getGeneratorAccountId()));
            addedRecentBlock.put((Object)"height", (Object)block.height);
            addedRecentBlock.put((Object)"version", (Object)block.version);
            addedRecentBlock.put((Object)"block", (Object)block.getStringId());
            addedRecentBlock.put((Object)"baseTarget", (Object)BigInteger.valueOf(block.baseTarget).multiply(BigInteger.valueOf(100000L)).divide(BigInteger.valueOf(153722867L)));
            addedRecentBlocks.add((Object)addedRecentBlock);
            final JSONObject response = new JSONObject();
            response.put((Object)"response", (Object)"processNewData");
            response.put((Object)"addedConfirmedTransactions", (Object)addedConfirmedTransactions);
            if (removedUnconfirmedTransactions.size() > 0) {
                response.put((Object)"removedUnconfirmedTransactions", (Object)removedUnconfirmedTransactions);
            }
            response.put((Object)"addedRecentBlocks", (Object)addedRecentBlocks);
            for (final User user : Nxt.users.values()) {
                user.send(response);
            }
            return true;
        }
        
        static void saveBlocks(final String fileName, final boolean flag) {
            try (final FileOutputStream fileOutputStream = new FileOutputStream(fileName);
                 final ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                objectOutputStream.writeInt(Nxt.blockCounter.get());
                objectOutputStream.writeObject(new HashMap(Nxt.blocks));
            }
            catch (IOException e) {
                Nxt.logMessage("Error saving blocks to " + fileName, e);
                throw new RuntimeException(e);
            }
        }
        
        boolean verifyBlockSignature() {
            final Account account = Nxt.accounts.get(this.getGeneratorAccountId());
            if (account == null) {
                return false;
            }
            final byte[] data = this.getBytes();
            final byte[] data2 = new byte[data.length - 64];
            System.arraycopy(data, 0, data2, 0, data2.length);
            return Crypto.verify(this.blockSignature, data2, this.generatorPublicKey) && account.setOrVerify(this.generatorPublicKey);
        }
        
        boolean verifyGenerationSignature() {
            try {
                final Block previousBlock = Nxt.blocks.get(this.previousBlock);
                if (previousBlock == null) {
                    return false;
                }
                if (this.version == 1 && !Crypto.verify(this.generationSignature, previousBlock.generationSignature, this.generatorPublicKey)) {
                    return false;
                }
                final Account account = Nxt.accounts.get(this.getGeneratorAccountId());
                if (account == null || account.getEffectiveBalance() <= 0) {
                    return false;
                }
                final int elapsedTime = this.timestamp - previousBlock.timestamp;
                final BigInteger target = BigInteger.valueOf(Nxt.lastBlock.get().baseTarget).multiply(BigInteger.valueOf(account.getEffectiveBalance())).multiply(BigInteger.valueOf(elapsedTime));
                final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                byte[] generationSignatureHash;
                if (this.version == 1) {
                    generationSignatureHash = digest.digest(this.generationSignature);
                }
                else {
                    digest.update(previousBlock.generationSignature);
                    generationSignatureHash = digest.digest(this.generatorPublicKey);
                    if (!Arrays.equals(this.generationSignature, generationSignatureHash)) {
                        return false;
                    }
                }
                final BigInteger hit = new BigInteger(1, new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0] });
                return hit.compareTo(target) < 0;
            }
            catch (RuntimeException e) {
                Nxt.logMessage("Error verifying block generation signature", e);
                return false;
            }
        }
        
        static {
            emptyLong = new long[0];
            emptyTransactions = new Transaction[0];
            heightComparator = new Comparator<Block>() {
                @Override
                public int compare(final Block o1, final Block o2) {
                    return (o1.height < o2.height) ? -1 : ((o1.height > o2.height) ? 1 : 0);
                }
            };
        }
    }
    
    static class Crypto
    {
        static byte[] getPublicKey(final String secretPhrase) {
            try {
                final byte[] publicKey = new byte[32];
                Curve25519.keygen(publicKey, null, Nxt.getMessageDigest("SHA-256").digest(secretPhrase.getBytes("UTF-8")));
                return publicKey;
            }
            catch (RuntimeException | UnsupportedEncodingException e) {
                Nxt.logMessage("Error getting public key", e);
                return null;
            }
        }
        
        static byte[] sign(final byte[] message, final String secretPhrase) {
            try {
                final byte[] P = new byte[32];
                final byte[] s = new byte[32];
                final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                Curve25519.keygen(P, s, digest.digest(secretPhrase.getBytes("UTF-8")));
                final byte[] m = digest.digest(message);
                digest.update(m);
                final byte[] x = digest.digest(s);
                final byte[] Y = new byte[32];
                Curve25519.keygen(Y, null, x);
                digest.update(m);
                final byte[] h = digest.digest(Y);
                final byte[] v = new byte[32];
                Curve25519.sign(v, h, x, s);
                final byte[] signature = new byte[64];
                System.arraycopy(v, 0, signature, 0, 32);
                System.arraycopy(h, 0, signature, 32, 32);
                return signature;
            }
            catch (RuntimeException | UnsupportedEncodingException e) {
                Nxt.logMessage("Error in signing message", e);
                return null;
            }
        }
        
        static boolean verify(final byte[] signature, final byte[] message, final byte[] publicKey) {
            try {
                final byte[] Y = new byte[32];
                final byte[] v = new byte[32];
                System.arraycopy(signature, 0, v, 0, 32);
                final byte[] h = new byte[32];
                System.arraycopy(signature, 32, h, 0, 32);
                Curve25519.verify(Y, v, h, publicKey);
                final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                final byte[] m = digest.digest(message);
                digest.update(m);
                final byte[] h2 = digest.digest(Y);
                return Arrays.equals(h, h2);
            }
            catch (RuntimeException e) {
                Nxt.logMessage("Error in Nxt.Crypto verify", e);
                return false;
            }
        }
    }
    
    static class Curve25519
    {
        public static final int KEY_SIZE = 32;
        public static final byte[] ZERO;
        public static final byte[] PRIME;
        public static final byte[] ORDER;
        private static final int P25 = 33554431;
        private static final int P26 = 67108863;
        private static final byte[] ORDER_TIMES_8;
        private static final long10 BASE_2Y;
        private static final long10 BASE_R2Y;
        
        public static final void clamp(final byte[] k) {
            final int n = 31;
            k[n] &= 0x7F;
            final int n2 = 31;
            k[n2] |= 0x40;
            final int n3 = 0;
            k[n3] &= 0xF8;
        }
        
        public static final void keygen(final byte[] P, final byte[] s, final byte[] k) {
            clamp(k);
            core(P, s, k, null);
        }
        
        public static final void curve(final byte[] Z, final byte[] k, final byte[] P) {
            core(Z, null, k, P);
        }
        
        public static final boolean sign(final byte[] v, final byte[] h, final byte[] x, final byte[] s) {
            final byte[] tmp1 = new byte[65];
            final byte[] tmp2 = new byte[33];
            for (int i = 0; i < 32; ++i) {
                v[i] = 0;
            }
            int i = mula_small(v, x, 0, h, 32, -1);
            mula_small(v, v, 0, Curve25519.ORDER, 32, (15 - v[31]) / 16);
            mula32(tmp1, v, s, 32, 1);
            divmod(tmp2, tmp1, 64, Curve25519.ORDER, 32);
            int w = 0;
            for (i = 0; i < 32; ++i) {
                final int n = w;
                final int n2 = i;
                final byte b = tmp1[i];
                v[n2] = b;
                w = (n | b);
            }
            return w != 0;
        }
        
        public static final void verify(final byte[] Y, final byte[] v, final byte[] h, final byte[] P) {
            final byte[] d = new byte[32];
            final long10[] p = { new long10(), new long10() };
            final long10[] s = { new long10(), new long10() };
            final long10[] yx = { new long10(), new long10(), new long10() };
            final long10[] yz = { new long10(), new long10(), new long10() };
            final long10[] t1 = { new long10(), new long10(), new long10() };
            final long10[] t2 = { new long10(), new long10(), new long10() };
            int vi = 0;
            int hi = 0;
            int di = 0;
            int nvh = 0;
            set(p[0], 9);
            unpack(p[1], P);
            x_to_y2(t1[0], t2[0], p[1]);
            sqrt(t1[0], t2[0]);
            int j = is_negative(t1[0]);
            final long10 long10 = t2[0];
            long10._0 += 39420360L;
            mul(t2[1], Curve25519.BASE_2Y, t1[0]);
            sub(t1[j], t2[0], t2[1]);
            add(t1[1 - j], t2[0], t2[1]);
            cpy(t2[0], p[1]);
            final long10 long2 = t2[0];
            long2._0 -= 9L;
            sqr(t2[1], t2[0]);
            recip(t2[0], t2[1], 0);
            mul(s[0], t1[0], t2[0]);
            sub(s[0], s[0], p[1]);
            final long10 long3 = s[0];
            long3._0 -= 486671L;
            mul(s[1], t1[1], t2[0]);
            sub(s[1], s[1], p[1]);
            final long10 long4 = s[1];
            long4._0 -= 486671L;
            mul_small(s[0], s[0], 1L);
            mul_small(s[1], s[1], 1L);
            for (int i = 0; i < 32; ++i) {
                vi = (vi >> 8 ^ (v[i] & 0xFF) ^ (v[i] & 0xFF) << 1);
                hi = (hi >> 8 ^ (h[i] & 0xFF) ^ (h[i] & 0xFF) << 1);
                nvh = (vi ^ hi ^ -1);
                di = ((nvh & (di & 0x80) >> 7) ^ vi);
                di ^= (nvh & (di & 0x1) << 1);
                di ^= (nvh & (di & 0x2) << 1);
                di ^= (nvh & (di & 0x4) << 1);
                di ^= (nvh & (di & 0x8) << 1);
                di ^= (nvh & (di & 0x10) << 1);
                di ^= (nvh & (di & 0x20) << 1);
                di ^= (nvh & (di & 0x40) << 1);
                d[i] = (byte)di;
            }
            di = ((nvh & (di & 0x80) << 1) ^ vi) >> 8;
            set(yx[0], 1);
            cpy(yx[1], p[di]);
            cpy(yx[2], s[0]);
            set(yz[0], 0);
            set(yz[1], 1);
            set(yz[2], 1);
            vi = 0;
            hi = 0;
            int i = 32;
            while (i-- != 0) {
                vi = (vi << 8 | (v[i] & 0xFF));
                hi = (hi << 8 | (h[i] & 0xFF));
                di = (di << 8 | (d[i] & 0xFF));
                j = 8;
                while (j-- != 0) {
                    mont_prep(t1[0], t2[0], yx[0], yz[0]);
                    mont_prep(t1[1], t2[1], yx[1], yz[1]);
                    mont_prep(t1[2], t2[2], yx[2], yz[2]);
                    int k = ((vi ^ vi >> 1) >> j & 0x1) + ((hi ^ hi >> 1) >> j & 0x1);
                    mont_dbl(yx[2], yz[2], t1[k], t2[k], yx[0], yz[0]);
                    k = ((di >> j & 0x2) ^ (di >> j & 0x1) << 1);
                    mont_add(t1[1], t2[1], t1[k], t2[k], yx[1], yz[1], p[di >> j & 0x1]);
                    mont_add(t1[2], t2[2], t1[0], t2[0], yx[2], yz[2], s[((vi ^ hi) >> j & 0x2) >> 1]);
                }
            }
            int k = (vi & 0x1) + (hi & 0x1);
            recip(t1[0], yz[k], 0);
            mul(t1[1], yx[k], t1[0]);
            pack(t1[1], Y);
        }
        
        private static final void cpy32(final byte[] d, final byte[] s) {
            for (int i = 0; i < 32; ++i) {
                d[i] = s[i];
            }
        }
        
        private static final int mula_small(final byte[] p, final byte[] q, final int m, final byte[] x, final int n, final int z) {
            int v = 0;
            for (int i = 0; i < n; ++i) {
                v += (q[i + m] & 0xFF) + z * (x[i] & 0xFF);
                p[i + m] = (byte)v;
                v >>= 8;
            }
            return v;
        }
        
        private static final int mula32(final byte[] p, final byte[] x, final byte[] y, final int t, final int z) {
            final int n = 31;
            int w = 0;
            int i;
            for (i = 0; i < t; ++i) {
                final int zy = z * (y[i] & 0xFF);
                w += mula_small(p, p, i, x, 31, zy) + (p[i + 31] & 0xFF) + zy * (x[31] & 0xFF);
                p[i + 31] = (byte)w;
                w >>= 8;
            }
            p[i + 31] = (byte)(w + (p[i + 31] & 0xFF));
            return w >> 8;
        }
        
        private static final void divmod(final byte[] q, final byte[] r, int n, final byte[] d, final int t) {
            int rn = 0;
            int dt = (d[t - 1] & 0xFF) << 8;
            if (t > 1) {
                dt |= (d[t - 2] & 0xFF);
            }
            while (n-- >= t) {
                int z = rn << 16 | (r[n] & 0xFF) << 8;
                if (n > 0) {
                    z |= (r[n - 1] & 0xFF);
                }
                z /= dt;
                rn += mula_small(r, r, n - t + 1, d, t, -z);
                q[n - t + 1] = (byte)(z + rn & 0xFF);
                mula_small(r, r, n - t + 1, d, t, -rn);
                rn = (r[n] & 0xFF);
                r[n] = 0;
            }
            r[t - 1] = (byte)rn;
        }
        
        private static final int numsize(final byte[] x, int n) {
            while (n-- != 0 && x[n] == 0) {}
            return n + 1;
        }
        
        private static final byte[] egcd32(final byte[] x, final byte[] y, final byte[] a, final byte[] b) {
            int bn = 32;
            for (int i = 0; i < 32; ++i) {
                x[i] = (y[i] = 0);
            }
            x[0] = 1;
            int an = numsize(a, 32);
            if (an == 0) {
                return y;
            }
            final byte[] temp = new byte[32];
            while (true) {
                int qn = bn - an + 1;
                divmod(temp, b, bn, a, an);
                bn = numsize(b, bn);
                if (bn == 0) {
                    return x;
                }
                mula32(y, x, temp, qn, -1);
                qn = an - bn + 1;
                divmod(temp, a, an, b, bn);
                an = numsize(a, an);
                if (an == 0) {
                    return y;
                }
                mula32(x, y, temp, qn, -1);
            }
        }
        
        private static final void unpack(final long10 x, final byte[] m) {
            x._0 = ((m[0] & 0xFF) | (m[1] & 0xFF) << 8 | (m[2] & 0xFF) << 16 | (m[3] & 0xFF & 0x3) << 24);
            x._1 = ((m[3] & 0xFF & 0xFFFFFFFC) >> 2 | (m[4] & 0xFF) << 6 | (m[5] & 0xFF) << 14 | (m[6] & 0xFF & 0x7) << 22);
            x._2 = ((m[6] & 0xFF & 0xFFFFFFF8) >> 3 | (m[7] & 0xFF) << 5 | (m[8] & 0xFF) << 13 | (m[9] & 0xFF & 0x1F) << 21);
            x._3 = ((m[9] & 0xFF & 0xFFFFFFE0) >> 5 | (m[10] & 0xFF) << 3 | (m[11] & 0xFF) << 11 | (m[12] & 0xFF & 0x3F) << 19);
            x._4 = ((m[12] & 0xFF & 0xFFFFFFC0) >> 6 | (m[13] & 0xFF) << 2 | (m[14] & 0xFF) << 10 | (m[15] & 0xFF) << 18);
            x._5 = ((m[16] & 0xFF) | (m[17] & 0xFF) << 8 | (m[18] & 0xFF) << 16 | (m[19] & 0xFF & 0x1) << 24);
            x._6 = ((m[19] & 0xFF & 0xFFFFFFFE) >> 1 | (m[20] & 0xFF) << 7 | (m[21] & 0xFF) << 15 | (m[22] & 0xFF & 0x7) << 23);
            x._7 = ((m[22] & 0xFF & 0xFFFFFFF8) >> 3 | (m[23] & 0xFF) << 5 | (m[24] & 0xFF) << 13 | (m[25] & 0xFF & 0xF) << 21);
            x._8 = ((m[25] & 0xFF & 0xFFFFFFF0) >> 4 | (m[26] & 0xFF) << 4 | (m[27] & 0xFF) << 12 | (m[28] & 0xFF & 0x3F) << 20);
            x._9 = ((m[28] & 0xFF & 0xFFFFFFC0) >> 6 | (m[29] & 0xFF) << 2 | (m[30] & 0xFF) << 10 | (m[31] & 0xFF) << 18);
        }
        
        private static final boolean is_overflow(final long10 x) {
            return (x._0 > 67108844L && (x._1 & x._3 & x._5 & x._7 & x._9) == 0x1FFFFFFL && (x._2 & x._4 & x._6 & x._8) == 0x3FFFFFFL) || x._9 > 33554431L;
        }
        
        private static final void pack(final long10 x, final byte[] m) {
            int ld = 0;
            int ud = 0;
            ld = (is_overflow(x) ? 1 : 0) - ((x._9 < 0L) ? 1 : 0);
            ud = ld * -33554432;
            ld *= 19;
            long t = ld + x._0 + (x._1 << 26);
            m[0] = (byte)t;
            m[1] = (byte)(t >> 8);
            m[2] = (byte)(t >> 16);
            m[3] = (byte)(t >> 24);
            t = (t >> 32) + (x._2 << 19);
            m[4] = (byte)t;
            m[5] = (byte)(t >> 8);
            m[6] = (byte)(t >> 16);
            m[7] = (byte)(t >> 24);
            t = (t >> 32) + (x._3 << 13);
            m[8] = (byte)t;
            m[9] = (byte)(t >> 8);
            m[10] = (byte)(t >> 16);
            m[11] = (byte)(t >> 24);
            t = (t >> 32) + (x._4 << 6);
            m[12] = (byte)t;
            m[13] = (byte)(t >> 8);
            m[14] = (byte)(t >> 16);
            m[15] = (byte)(t >> 24);
            t = (t >> 32) + x._5 + (x._6 << 25);
            m[16] = (byte)t;
            m[17] = (byte)(t >> 8);
            m[18] = (byte)(t >> 16);
            m[19] = (byte)(t >> 24);
            t = (t >> 32) + (x._7 << 19);
            m[20] = (byte)t;
            m[21] = (byte)(t >> 8);
            m[22] = (byte)(t >> 16);
            m[23] = (byte)(t >> 24);
            t = (t >> 32) + (x._8 << 12);
            m[24] = (byte)t;
            m[25] = (byte)(t >> 8);
            m[26] = (byte)(t >> 16);
            m[27] = (byte)(t >> 24);
            t = (t >> 32) + (x._9 + ud << 6);
            m[28] = (byte)t;
            m[29] = (byte)(t >> 8);
            m[30] = (byte)(t >> 16);
            m[31] = (byte)(t >> 24);
        }
        
        private static final void cpy(final long10 out, final long10 in) {
            out._0 = in._0;
            out._1 = in._1;
            out._2 = in._2;
            out._3 = in._3;
            out._4 = in._4;
            out._5 = in._5;
            out._6 = in._6;
            out._7 = in._7;
            out._8 = in._8;
            out._9 = in._9;
        }
        
        private static final void set(final long10 out, final int in) {
            out._0 = in;
            out._1 = 0L;
            out._2 = 0L;
            out._3 = 0L;
            out._4 = 0L;
            out._5 = 0L;
            out._6 = 0L;
            out._7 = 0L;
            out._8 = 0L;
            out._9 = 0L;
        }
        
        private static final void add(final long10 xy, final long10 x, final long10 y) {
            xy._0 = x._0 + y._0;
            xy._1 = x._1 + y._1;
            xy._2 = x._2 + y._2;
            xy._3 = x._3 + y._3;
            xy._4 = x._4 + y._4;
            xy._5 = x._5 + y._5;
            xy._6 = x._6 + y._6;
            xy._7 = x._7 + y._7;
            xy._8 = x._8 + y._8;
            xy._9 = x._9 + y._9;
        }
        
        private static final void sub(final long10 xy, final long10 x, final long10 y) {
            xy._0 = x._0 - y._0;
            xy._1 = x._1 - y._1;
            xy._2 = x._2 - y._2;
            xy._3 = x._3 - y._3;
            xy._4 = x._4 - y._4;
            xy._5 = x._5 - y._5;
            xy._6 = x._6 - y._6;
            xy._7 = x._7 - y._7;
            xy._8 = x._8 - y._8;
            xy._9 = x._9 - y._9;
        }
        
        private static final long10 mul_small(final long10 xy, final long10 x, final long y) {
            long t = x._8 * y;
            xy._8 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x._9 * y;
            xy._9 = (t & 0x1FFFFFFL);
            t = 19L * (t >> 25) + x._0 * y;
            xy._0 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x._1 * y;
            xy._1 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x._2 * y;
            xy._2 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x._3 * y;
            xy._3 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x._4 * y;
            xy._4 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x._5 * y;
            xy._5 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x._6 * y;
            xy._6 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x._7 * y;
            xy._7 = (t & 0x1FFFFFFL);
            t = (t >> 25) + xy._8;
            xy._8 = (t & 0x3FFFFFFL);
            xy._9 += t >> 26;
            return xy;
        }
        
        private static final long10 mul(final long10 xy, final long10 x, final long10 y) {
            final long x_0 = x._0;
            final long x_ = x._1;
            final long x_2 = x._2;
            final long x_3 = x._3;
            final long x_4 = x._4;
            final long x_5 = x._5;
            final long x_6 = x._6;
            final long x_7 = x._7;
            final long x_8 = x._8;
            final long x_9 = x._9;
            final long y_0 = y._0;
            final long y_ = y._1;
            final long y_2 = y._2;
            final long y_3 = y._3;
            final long y_4 = y._4;
            final long y_5 = y._5;
            final long y_6 = y._6;
            final long y_7 = y._7;
            final long y_8 = y._8;
            final long y_9 = y._9;
            long t = x_0 * y_8 + x_2 * y_6 + x_4 * y_4 + x_6 * y_2 + x_8 * y_0 + 2L * (x_ * y_7 + x_3 * y_5 + x_5 * y_3 + x_7 * y_) + 38L * (x_9 * y_9);
            xy._8 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x_0 * y_9 + x_ * y_8 + x_2 * y_7 + x_3 * y_6 + x_4 * y_5 + x_5 * y_4 + x_6 * y_3 + x_7 * y_2 + x_8 * y_ + x_9 * y_0;
            xy._9 = (t & 0x1FFFFFFL);
            t = x_0 * y_0 + 19L * ((t >> 25) + x_2 * y_8 + x_4 * y_6 + x_6 * y_4 + x_8 * y_2) + 38L * (x_ * y_9 + x_3 * y_7 + x_5 * y_5 + x_7 * y_3 + x_9 * y_);
            xy._0 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x_0 * y_ + x_ * y_0 + 19L * (x_2 * y_9 + x_3 * y_8 + x_4 * y_7 + x_5 * y_6 + x_6 * y_5 + x_7 * y_4 + x_8 * y_3 + x_9 * y_2);
            xy._1 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x_0 * y_2 + x_2 * y_0 + 19L * (x_4 * y_8 + x_6 * y_6 + x_8 * y_4) + 2L * (x_ * y_) + 38L * (x_3 * y_9 + x_5 * y_7 + x_7 * y_5 + x_9 * y_3);
            xy._2 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x_0 * y_3 + x_ * y_2 + x_2 * y_ + x_3 * y_0 + 19L * (x_4 * y_9 + x_5 * y_8 + x_6 * y_7 + x_7 * y_6 + x_8 * y_5 + x_9 * y_4);
            xy._3 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x_0 * y_4 + x_2 * y_2 + x_4 * y_0 + 19L * (x_6 * y_8 + x_8 * y_6) + 2L * (x_ * y_3 + x_3 * y_) + 38L * (x_5 * y_9 + x_7 * y_7 + x_9 * y_5);
            xy._4 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x_0 * y_5 + x_ * y_4 + x_2 * y_3 + x_3 * y_2 + x_4 * y_ + x_5 * y_0 + 19L * (x_6 * y_9 + x_7 * y_8 + x_8 * y_7 + x_9 * y_6);
            xy._5 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x_0 * y_6 + x_2 * y_4 + x_4 * y_2 + x_6 * y_0 + 19L * (x_8 * y_8) + 2L * (x_ * y_5 + x_3 * y_3 + x_5 * y_) + 38L * (x_7 * y_9 + x_9 * y_7);
            xy._6 = (t & 0x3FFFFFFL);
            t = (t >> 26) + x_0 * y_7 + x_ * y_6 + x_2 * y_5 + x_3 * y_4 + x_4 * y_3 + x_5 * y_2 + x_6 * y_ + x_7 * y_0 + 19L * (x_8 * y_9 + x_9 * y_8);
            xy._7 = (t & 0x1FFFFFFL);
            t = (t >> 25) + xy._8;
            xy._8 = (t & 0x3FFFFFFL);
            xy._9 += t >> 26;
            return xy;
        }
        
        private static final long10 sqr(final long10 x2, final long10 x) {
            final long x_0 = x._0;
            final long x_ = x._1;
            final long x_2 = x._2;
            final long x_3 = x._3;
            final long x_4 = x._4;
            final long x_5 = x._5;
            final long x_6 = x._6;
            final long x_7 = x._7;
            final long x_8 = x._8;
            final long x_9 = x._9;
            long t = x_4 * x_4 + 2L * (x_0 * x_8 + x_2 * x_6) + 38L * (x_9 * x_9) + 4L * (x_ * x_7 + x_3 * x_5);
            x2._8 = (t & 0x3FFFFFFL);
            t = (t >> 26) + 2L * (x_0 * x_9 + x_ * x_8 + x_2 * x_7 + x_3 * x_6 + x_4 * x_5);
            x2._9 = (t & 0x1FFFFFFL);
            t = 19L * (t >> 25) + x_0 * x_0 + 38L * (x_2 * x_8 + x_4 * x_6 + x_5 * x_5) + 76L * (x_ * x_9 + x_3 * x_7);
            x2._0 = (t & 0x3FFFFFFL);
            t = (t >> 26) + 2L * (x_0 * x_) + 38L * (x_2 * x_9 + x_3 * x_8 + x_4 * x_7 + x_5 * x_6);
            x2._1 = (t & 0x1FFFFFFL);
            t = (t >> 25) + 19L * (x_6 * x_6) + 2L * (x_0 * x_2 + x_ * x_) + 38L * (x_4 * x_8) + 76L * (x_3 * x_9 + x_5 * x_7);
            x2._2 = (t & 0x3FFFFFFL);
            t = (t >> 26) + 2L * (x_0 * x_3 + x_ * x_2) + 38L * (x_4 * x_9 + x_5 * x_8 + x_6 * x_7);
            x2._3 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x_2 * x_2 + 2L * (x_0 * x_4) + 38L * (x_6 * x_8 + x_7 * x_7) + 4L * (x_ * x_3) + 76L * (x_5 * x_9);
            x2._4 = (t & 0x3FFFFFFL);
            t = (t >> 26) + 2L * (x_0 * x_5 + x_ * x_4 + x_2 * x_3) + 38L * (x_6 * x_9 + x_7 * x_8);
            x2._5 = (t & 0x1FFFFFFL);
            t = (t >> 25) + 19L * (x_8 * x_8) + 2L * (x_0 * x_6 + x_2 * x_4 + x_3 * x_3) + 4L * (x_ * x_5) + 76L * (x_7 * x_9);
            x2._6 = (t & 0x3FFFFFFL);
            t = (t >> 26) + 2L * (x_0 * x_7 + x_ * x_6 + x_2 * x_5 + x_3 * x_4) + 38L * (x_8 * x_9);
            x2._7 = (t & 0x1FFFFFFL);
            t = (t >> 25) + x2._8;
            x2._8 = (t & 0x3FFFFFFL);
            x2._9 += t >> 26;
            return x2;
        }
        
        private static final void recip(final long10 y, final long10 x, final int sqrtassist) {
            final long10 t0 = new long10();
            final long10 t = new long10();
            final long10 t2 = new long10();
            final long10 t3 = new long10();
            final long10 t4 = new long10();
            sqr(t, x);
            sqr(t2, t);
            sqr(t0, t2);
            mul(t2, t0, x);
            mul(t0, t2, t);
            sqr(t, t0);
            mul(t3, t, t2);
            sqr(t, t3);
            sqr(t2, t);
            sqr(t, t2);
            sqr(t2, t);
            sqr(t, t2);
            mul(t2, t, t3);
            sqr(t, t2);
            sqr(t3, t);
            for (int i = 1; i < 5; ++i) {
                sqr(t, t3);
                sqr(t3, t);
            }
            mul(t, t3, t2);
            sqr(t3, t);
            sqr(t4, t3);
            for (int i = 1; i < 10; ++i) {
                sqr(t3, t4);
                sqr(t4, t3);
            }
            mul(t3, t4, t);
            for (int i = 0; i < 5; ++i) {
                sqr(t, t3);
                sqr(t3, t);
            }
            mul(t, t3, t2);
            sqr(t2, t);
            sqr(t3, t2);
            for (int i = 1; i < 25; ++i) {
                sqr(t2, t3);
                sqr(t3, t2);
            }
            mul(t2, t3, t);
            sqr(t3, t2);
            sqr(t4, t3);
            for (int i = 1; i < 50; ++i) {
                sqr(t3, t4);
                sqr(t4, t3);
            }
            mul(t3, t4, t2);
            for (int i = 0; i < 25; ++i) {
                sqr(t4, t3);
                sqr(t3, t4);
            }
            mul(t2, t3, t);
            sqr(t, t2);
            sqr(t2, t);
            if (sqrtassist != 0) {
                mul(y, x, t2);
            }
            else {
                sqr(t, t2);
                sqr(t2, t);
                sqr(t, t2);
                mul(y, t, t0);
            }
        }
        
        private static final int is_negative(final long10 x) {
            return (int)(((is_overflow(x) || x._9 < 0L) ? 1 : 0) ^ (x._0 & 0x1L));
        }
        
        private static final void sqrt(final long10 x, final long10 u) {
            final long10 v = new long10();
            final long10 t1 = new long10();
            final long10 t2 = new long10();
            add(t1, u, u);
            recip(v, t1, 1);
            sqr(x, v);
            mul(t2, t1, x);
            final long10 long10 = t2;
            --long10._0;
            mul(t1, v, t2);
            mul(x, u, t1);
        }
        
        private static final void mont_prep(final long10 t1, final long10 t2, final long10 ax, final long10 az) {
            add(t1, ax, az);
            sub(t2, ax, az);
        }
        
        private static final void mont_add(final long10 t1, final long10 t2, final long10 t3, final long10 t4, final long10 ax, final long10 az, final long10 dx) {
            mul(ax, t2, t3);
            mul(az, t1, t4);
            add(t1, ax, az);
            sub(t2, ax, az);
            sqr(ax, t1);
            sqr(t1, t2);
            mul(az, t1, dx);
        }
        
        private static final void mont_dbl(final long10 t1, final long10 t2, final long10 t3, final long10 t4, final long10 bx, final long10 bz) {
            sqr(t1, t3);
            sqr(t2, t4);
            mul(bx, t1, t2);
            sub(t2, t1, t2);
            mul_small(bz, t2, 121665L);
            add(t1, t1, bz);
            mul(bz, t1, t2);
        }
        
        private static final void x_to_y2(final long10 t, final long10 y2, final long10 x) {
            sqr(t, x);
            mul_small(y2, x, 486662L);
            add(t, t, y2);
            ++t._0;
            mul(y2, t, x);
        }
        
        private static final void core(final byte[] Px, final byte[] s, final byte[] k, final byte[] Gx) {
            final long10 dx = new long10();
            final long10 t1 = new long10();
            final long10 t2 = new long10();
            final long10 t3 = new long10();
            final long10 t4 = new long10();
            final long10[] x = { new long10(), new long10() };
            final long10[] z = { new long10(), new long10() };
            if (Gx != null) {
                unpack(dx, Gx);
            }
            else {
                set(dx, 9);
            }
            set(x[0], 1);
            set(z[0], 0);
            cpy(x[1], dx);
            set(z[1], 1);
            int i = 32;
            while (i-- != 0) {
                if (i == 0) {
                    i = 0;
                }
                int j = 8;
                while (j-- != 0) {
                    final int bit1 = (k[i] & 0xFF) >> j & 0x1;
                    final int bit2 = ((k[i] & 0xFF) ^ -1) >> j & 0x1;
                    final long10 ax = x[bit2];
                    final long10 az = z[bit2];
                    final long10 bx = x[bit1];
                    final long10 bz = z[bit1];
                    mont_prep(t1, t2, ax, az);
                    mont_prep(t3, t4, bx, bz);
                    mont_add(t1, t2, t3, t4, ax, az, dx);
                    mont_dbl(t1, t2, t3, t4, bx, bz);
                }
            }
            recip(t1, z[0], 0);
            mul(dx, x[0], t1);
            pack(dx, Px);
            if (s != null) {
                x_to_y2(t2, t1, dx);
                recip(t3, z[1], 0);
                mul(t2, x[1], t3);
                add(t2, t2, dx);
                final long10 long10 = t2;
                long10._0 += 486671L;
                final long10 long2 = dx;
                long2._0 -= 9L;
                sqr(t3, dx);
                mul(dx, t2, t3);
                sub(dx, dx, t1);
                final long10 long3 = dx;
                long3._0 -= 39420360L;
                mul(t1, dx, Curve25519.BASE_R2Y);
                if (is_negative(t1) != 0) {
                    cpy32(s, k);
                }
                else {
                    mula_small(s, Curve25519.ORDER_TIMES_8, 0, k, 32, -1);
                }
                final byte[] temp1 = new byte[32];
                final byte[] temp2 = new byte[64];
                final byte[] temp3 = new byte[64];
                cpy32(temp1, Curve25519.ORDER);
                cpy32(s, egcd32(temp2, temp3, s, temp1));
                if ((s[31] & 0x80) != 0x0) {
                    mula_small(s, s, 0, Curve25519.ORDER, 32, 1);
                }
            }
        }
        
        static {
            ZERO = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
            PRIME = new byte[] { -19, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 127 };
            ORDER = new byte[] { -19, -45, -11, 92, 26, 99, 18, 88, -42, -100, -9, -94, -34, -7, -34, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 16 };
            ORDER_TIMES_8 = new byte[] { 104, -97, -82, -25, -46, 24, -109, -64, -78, -26, -68, 23, -11, -50, -9, -90, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -128 };
            BASE_2Y = new long10(39999547L, 18689728L, 59995525L, 1648697L, 57546132L, 24010086L, 19059592L, 5425144L, 63499247L, 16420658L);
            BASE_R2Y = new long10(5744L, 8160848L, 4790893L, 13779497L, 35730846L, 12541209L, 49101323L, 30047407L, 40071253L, 6226132L);
        }
        
        private static final class long10
        {
            public long _0;
            public long _1;
            public long _2;
            public long _3;
            public long _4;
            public long _5;
            public long _6;
            public long _7;
            public long _8;
            public long _9;
            
            public long10() {
                super();
            }
            
            public long10(final long _0, final long _1, final long _2, final long _3, final long _4, final long _5, final long _6, final long _7, final long _8, final long _9) {
                super();
                this._0 = _0;
                this._1 = _1;
                this._2 = _2;
                this._3 = _3;
                this._4 = _4;
                this._5 = _5;
                this._6 = _6;
                this._7 = _7;
                this._8 = _8;
                this._9 = _9;
            }
        }
    }
    
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
    
    static class Transaction implements Comparable<Transaction>, Serializable
    {
        static final long serialVersionUID = 0L;
        static final byte TYPE_PAYMENT = 0;
        static final byte TYPE_MESSAGING = 1;
        static final byte TYPE_COLORED_COINS = 2;
        static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
        static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
        static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
        static final byte SUBTYPE_COLORED_COINS_ASSET_ISSUANCE = 0;
        static final byte SUBTYPE_COLORED_COINS_ASSET_TRANSFER = 1;
        static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT = 2;
        static final byte SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT = 3;
        static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION = 4;
        static final byte SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION = 5;
        static final int ASSET_ISSUANCE_FEE = 1000;
        final byte type;
        final byte subtype;
        int timestamp;
        final short deadline;
        final byte[] senderPublicKey;
        final long recipient;
        final int amount;
        final int fee;
        final long referencedTransaction;
        byte[] signature;
        Attachment attachment;
        int index;
        long block;
        int height;
        public static final Comparator<Transaction> timestampComparator;
        private static final int TRANSACTION_BYTES_LENGTH = 128;
        transient volatile long id;
        transient volatile String stringId;
        transient volatile long senderAccountId;
        
        Transaction(final byte type, final byte subtype, final int timestamp, final short deadline, final byte[] senderPublicKey, final long recipient, final int amount, final int fee, final long referencedTransaction, final byte[] signature) {
            super();
            this.stringId = null;
            this.type = type;
            this.subtype = subtype;
            this.timestamp = timestamp;
            this.deadline = deadline;
            this.senderPublicKey = senderPublicKey;
            this.recipient = recipient;
            this.amount = amount;
            this.fee = fee;
            this.referencedTransaction = referencedTransaction;
            this.signature = signature;
            this.height = Integer.MAX_VALUE;
        }
        
        @Override
        public int compareTo(final Transaction o) {
            if (this.height < o.height) {
                return -1;
            }
            if (this.height > o.height) {
                return 1;
            }
            if (this.fee * o.getSize() > o.fee * this.getSize()) {
                return -1;
            }
            if (this.fee * o.getSize() < o.fee * this.getSize()) {
                return 1;
            }
            if (this.timestamp < o.timestamp) {
                return -1;
            }
            if (this.timestamp > o.timestamp) {
                return 1;
            }
            if (this.index < o.index) {
                return -1;
            }
            if (this.index > o.index) {
                return 1;
            }
            return 0;
        }
        
        int getSize() {
            return 128 + ((this.attachment == null) ? 0 : this.attachment.getSize());
        }
        
        byte[] getBytes() {
            final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(this.type);
            buffer.put(this.subtype);
            buffer.putInt(this.timestamp);
            buffer.putShort(this.deadline);
            buffer.put(this.senderPublicKey);
            buffer.putLong(this.recipient);
            buffer.putInt(this.amount);
            buffer.putInt(this.fee);
            buffer.putLong(this.referencedTransaction);
            buffer.put(this.signature);
            if (this.attachment != null) {
                buffer.put(this.attachment.getBytes());
            }
            return buffer.array();
        }
        
        long getId() {
            this.calculateIds();
            return this.id;
        }
        
        String getStringId() {
            this.calculateIds();
            return this.stringId;
        }
        
        long getSenderAccountId() {
            this.calculateIds();
            return this.senderAccountId;
        }
        
        private void calculateIds() {
            if (this.stringId != null) {
                return;
            }
            final byte[] hash = Nxt.getMessageDigest("SHA-256").digest(this.getBytes());
            final BigInteger bigInteger = new BigInteger(1, new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
            this.id = bigInteger.longValue();
            this.stringId = bigInteger.toString();
            this.senderAccountId = Account.getId(this.senderPublicKey);
        }
        
        JSONObject getJSONObject() {
            final JSONObject transaction = new JSONObject();
            transaction.put((Object)"type", (Object)this.type);
            transaction.put((Object)"subtype", (Object)this.subtype);
            transaction.put((Object)"timestamp", (Object)this.timestamp);
            transaction.put((Object)"deadline", (Object)this.deadline);
            transaction.put((Object)"senderPublicKey", (Object)Nxt.convert(this.senderPublicKey));
            transaction.put((Object)"recipient", (Object)Nxt.convert(this.recipient));
            transaction.put((Object)"amount", (Object)this.amount);
            transaction.put((Object)"fee", (Object)this.fee);
            transaction.put((Object)"referencedTransaction", (Object)Nxt.convert(this.referencedTransaction));
            transaction.put((Object)"signature", (Object)Nxt.convert(this.signature));
            if (this.attachment != null) {
                transaction.put((Object)"attachment", (Object)this.attachment.getJSONObject());
            }
            return transaction;
        }
        
        long getRecipientDeltaBalance() {
            return this.amount * 100L + ((this.attachment == null) ? 0L : this.attachment.getRecipientDeltaBalance());
        }
        
        long getSenderDeltaBalance() {
            return -(this.amount + this.fee) * 100L + ((this.attachment == null) ? 0L : this.attachment.getSenderDeltaBalance());
        }
        
        static Transaction getTransaction(final ByteBuffer buffer) {
            final byte type = buffer.get();
            final byte subtype = buffer.get();
            final int timestamp = buffer.getInt();
            final short deadline = buffer.getShort();
            final byte[] senderPublicKey = new byte[32];
            buffer.get(senderPublicKey);
            final long recipient = buffer.getLong();
            final int amount = buffer.getInt();
            final int fee = buffer.getInt();
            final long referencedTransaction = buffer.getLong();
            final byte[] signature = new byte[64];
            buffer.get(signature);
            final Transaction transaction = new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);
            Label_0581: {
                switch (type) {
                    case 1: {
                        switch (subtype) {
                            case 0: {
                                final int messageLength = buffer.getInt();
                                if (messageLength <= 1000) {
                                    final byte[] message = new byte[messageLength];
                                    buffer.get(message);
                                    transaction.attachment = new MessagingArbitraryMessageAttachment(message);
                                }
                                break;
                            }
                            case 1: {
                                final int aliasLength = buffer.get();
                                final byte[] alias = new byte[aliasLength];
                                buffer.get(alias);
                                final int uriLength = buffer.getShort();
                                final byte[] uri = new byte[uriLength];
                                buffer.get(uri);
                                try {
                                    transaction.attachment = new MessagingAliasAssignmentAttachment(new String(alias, "UTF-8").intern(), new String(uri, "UTF-8").intern());
                                }
                                catch (RuntimeException | UnsupportedEncodingException e) {
                                    Nxt.logDebugMessage("Error parsing alias assignment", e);
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case 2: {
                        switch (subtype) {
                            case 0: {
                                final int nameLength = buffer.get();
                                final byte[] name = new byte[nameLength];
                                buffer.get(name);
                                final int descriptionLength = buffer.getShort();
                                final byte[] description = new byte[descriptionLength];
                                buffer.get(description);
                                final int quantity = buffer.getInt();
                                try {
                                    transaction.attachment = new ColoredCoinsAssetIssuanceAttachment(new String(name, "UTF-8").intern(), new String(description, "UTF-8").intern(), quantity);
                                }
                                catch (RuntimeException | UnsupportedEncodingException e2) {
                                    Nxt.logDebugMessage("Error in asset issuance", e2);
                                }
                                break Label_0581;
                            }
                            case 1: {
                                final long asset = buffer.getLong();
                                final int quantity2 = buffer.getInt();
                                transaction.attachment = new ColoredCoinsAssetTransferAttachment(asset, quantity2);
                                break Label_0581;
                            }
                            case 2: {
                                final long asset = buffer.getLong();
                                final int quantity2 = buffer.getInt();
                                final long price = buffer.getLong();
                                transaction.attachment = new ColoredCoinsAskOrderPlacementAttachment(asset, quantity2, price);
                                break Label_0581;
                            }
                            case 3: {
                                final long asset = buffer.getLong();
                                final int quantity2 = buffer.getInt();
                                final long price = buffer.getLong();
                                transaction.attachment = new ColoredCoinsBidOrderPlacementAttachment(asset, quantity2, price);
                                break Label_0581;
                            }
                            case 4: {
                                final long order = buffer.getLong();
                                transaction.attachment = new ColoredCoinsAskOrderCancellationAttachment(order);
                                break Label_0581;
                            }
                            case 5: {
                                final long order = buffer.getLong();
                                transaction.attachment = new ColoredCoinsBidOrderCancellationAttachment(order);
                                break Label_0581;
                            }
                        }
                        break;
                    }
                }
            }
            return transaction;
        }
        
        static Transaction getTransaction(final JSONObject transactionData) {
            final byte type = (byte)transactionData.get((Object)"type");
            final byte subtype = (byte)transactionData.get((Object)"subtype");
            final int timestamp = (int)transactionData.get((Object)"timestamp");
            final short deadline = (short)transactionData.get((Object)"deadline");
            final byte[] senderPublicKey = Nxt.convert((String)transactionData.get((Object)"senderPublicKey"));
            final long recipient = Nxt.parseUnsignedLong((String)transactionData.get((Object)"recipient"));
            final int amount = (int)transactionData.get((Object)"amount");
            final int fee = (int)transactionData.get((Object)"fee");
            final long referencedTransaction = Nxt.parseUnsignedLong((String)transactionData.get((Object)"referencedTransaction"));
            final byte[] signature = Nxt.convert((String)transactionData.get((Object)"signature"));
            final Transaction transaction = new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);
            final JSONObject attachmentData = (JSONObject)transactionData.get((Object)"attachment");
            Label_0648: {
                switch (type) {
                    case 1: {
                        switch (subtype) {
                            case 0: {
                                final String message = (String)attachmentData.get((Object)"message");
                                transaction.attachment = new MessagingArbitraryMessageAttachment(Nxt.convert(message));
                                break;
                            }
                            case 1: {
                                final String alias = (String)attachmentData.get((Object)"alias");
                                final String uri = (String)attachmentData.get((Object)"uri");
                                transaction.attachment = new MessagingAliasAssignmentAttachment(alias.trim(), uri.trim());
                                break;
                            }
                        }
                        break;
                    }
                    case 2: {
                        switch (subtype) {
                            case 0: {
                                final String name = (String)attachmentData.get((Object)"name");
                                final String description = (String)attachmentData.get((Object)"description");
                                final int quantity = (int)attachmentData.get((Object)"quantity");
                                transaction.attachment = new ColoredCoinsAssetIssuanceAttachment(name.trim(), description.trim(), quantity);
                                break Label_0648;
                            }
                            case 1: {
                                final long asset = Nxt.parseUnsignedLong((String)attachmentData.get((Object)"asset"));
                                final int quantity = (int)attachmentData.get((Object)"quantity");
                                transaction.attachment = new ColoredCoinsAssetTransferAttachment(asset, quantity);
                                break Label_0648;
                            }
                            case 2: {
                                final long asset = Nxt.parseUnsignedLong((String)attachmentData.get((Object)"asset"));
                                final int quantity = (int)attachmentData.get((Object)"quantity");
                                final long price = (long)attachmentData.get((Object)"price");
                                transaction.attachment = new ColoredCoinsAskOrderPlacementAttachment(asset, quantity, price);
                                break Label_0648;
                            }
                            case 3: {
                                final long asset = Nxt.parseUnsignedLong((String)attachmentData.get((Object)"asset"));
                                final int quantity = (int)attachmentData.get((Object)"quantity");
                                final long price = (long)attachmentData.get((Object)"price");
                                transaction.attachment = new ColoredCoinsBidOrderPlacementAttachment(asset, quantity, price);
                                break Label_0648;
                            }
                            case 4: {
                                transaction.attachment = new ColoredCoinsAskOrderCancellationAttachment(Nxt.parseUnsignedLong((String)attachmentData.get((Object)"order")));
                                break Label_0648;
                            }
                            case 5: {
                                transaction.attachment = new ColoredCoinsBidOrderCancellationAttachment(Nxt.parseUnsignedLong((String)attachmentData.get((Object)"order")));
                                break Label_0648;
                            }
                        }
                        break;
                    }
                }
            }
            return transaction;
        }
        
        static void loadTransactions(final String fileName) throws FileNotFoundException {
            try (final FileInputStream fileInputStream = new FileInputStream(fileName);
                 final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                Nxt.transactionCounter.set(objectInputStream.readInt());
                Nxt.transactions.clear();
                Nxt.transactions.putAll((Map<?, ?>)objectInputStream.readObject());
            }
            catch (FileNotFoundException e) {
                throw e;
            }
            catch (IOException | ClassNotFoundException e2) {
                Nxt.logMessage("Error loading transactions from " + fileName, e2);
                System.exit(1);
            }
        }
        
        static void processTransactions(final JSONObject request, final String parameterName) {
            final JSONArray transactionsData = (JSONArray)request.get((Object)parameterName);
            final JSONArray validTransactionsData = new JSONArray();
            for (final Object transactionData : transactionsData) {
                final Transaction transaction = getTransaction((JSONObject)transactionData);
                try {
                    final int curTime = Nxt.getEpochTime(System.currentTimeMillis());
                    if (transaction.timestamp > curTime + 15 || transaction.deadline < 1 || transaction.timestamp + transaction.deadline * 60 < curTime || transaction.fee <= 0 || !transaction.validateAttachment()) {
                        continue;
                    }
                    final long senderId;
                    boolean doubleSpendingTransaction;
                    synchronized (Nxt.blocksAndTransactionsLock) {
                        final long id = transaction.getId();
                        if (Nxt.transactions.get(id) != null || Nxt.unconfirmedTransactions.get(id) != null || Nxt.doubleSpendingTransactions.get(id) != null || !transaction.verify()) {
                            continue;
                        }
                        senderId = transaction.getSenderAccountId();
                        final Account account = Nxt.accounts.get(senderId);
                        if (account == null) {
                            doubleSpendingTransaction = true;
                        }
                        else {
                            final int amount = transaction.amount + transaction.fee;
                            synchronized (account) {
                                if (account.getUnconfirmedBalance() < amount * 100L) {
                                    doubleSpendingTransaction = true;
                                }
                                else {
                                    doubleSpendingTransaction = false;
                                    account.addToUnconfirmedBalance(-amount * 100L);
                                    if (transaction.type == 2) {
                                        if (transaction.subtype == 1) {
                                            final ColoredCoinsAssetTransferAttachment attachment = (ColoredCoinsAssetTransferAttachment)transaction.attachment;
                                            final Integer unconfirmedAssetBalance = account.getUnconfirmedAssetBalance(attachment.asset);
                                            if (unconfirmedAssetBalance == null || unconfirmedAssetBalance < attachment.quantity) {
                                                doubleSpendingTransaction = true;
                                                account.addToUnconfirmedBalance(amount * 100L);
                                            }
                                            else {
                                                account.addToUnconfirmedAssetBalance(attachment.asset, -attachment.quantity);
                                            }
                                        }
                                        else if (transaction.subtype == 2) {
                                            final ColoredCoinsAskOrderPlacementAttachment attachment2 = (ColoredCoinsAskOrderPlacementAttachment)transaction.attachment;
                                            final Integer unconfirmedAssetBalance = account.getUnconfirmedAssetBalance(attachment2.asset);
                                            if (unconfirmedAssetBalance == null || unconfirmedAssetBalance < attachment2.quantity) {
                                                doubleSpendingTransaction = true;
                                                account.addToUnconfirmedBalance(amount * 100L);
                                            }
                                            else {
                                                account.addToUnconfirmedAssetBalance(attachment2.asset, -attachment2.quantity);
                                            }
                                        }
                                        else if (transaction.subtype == 3) {
                                            final ColoredCoinsBidOrderPlacementAttachment attachment3 = (ColoredCoinsBidOrderPlacementAttachment)transaction.attachment;
                                            if (account.getUnconfirmedBalance() < attachment3.quantity * attachment3.price) {
                                                doubleSpendingTransaction = true;
                                                account.addToUnconfirmedBalance(amount * 100L);
                                            }
                                            else {
                                                account.addToUnconfirmedBalance(-attachment3.quantity * attachment3.price);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        transaction.index = Nxt.transactionCounter.incrementAndGet();
                        if (doubleSpendingTransaction) {
                            Nxt.doubleSpendingTransactions.put(transaction.getId(), transaction);
                        }
                        else {
                            Nxt.unconfirmedTransactions.put(transaction.getId(), transaction);
                            if (parameterName.equals("transactions")) {
                                validTransactionsData.add(transactionData);
                            }
                        }
                    }
                    final JSONObject response = new JSONObject();
                    response.put((Object)"response", (Object)"processNewData");
                    final JSONArray newTransactions = new JSONArray();
                    final JSONObject newTransaction = new JSONObject();
                    newTransaction.put((Object)"index", (Object)transaction.index);
                    newTransaction.put((Object)"timestamp", (Object)transaction.timestamp);
                    newTransaction.put((Object)"deadline", (Object)transaction.deadline);
                    newTransaction.put((Object)"recipient", (Object)Nxt.convert(transaction.recipient));
                    newTransaction.put((Object)"amount", (Object)transaction.amount);
                    newTransaction.put((Object)"fee", (Object)transaction.fee);
                    newTransaction.put((Object)"sender", (Object)Nxt.convert(senderId));
                    newTransaction.put((Object)"id", (Object)transaction.getStringId());
                    newTransactions.add((Object)newTransaction);
                    if (doubleSpendingTransaction) {
                        response.put((Object)"addedDoubleSpendingTransactions", (Object)newTransactions);
                    }
                    else {
                        response.put((Object)"addedUnconfirmedTransactions", (Object)newTransactions);
                    }
                    for (final User user : Nxt.users.values()) {
                        user.send(response);
                    }
                }
                catch (RuntimeException e) {
                    Nxt.logMessage("Error processing transaction", e);
                }
            }
            if (validTransactionsData.size() > 0) {
                final JSONObject peerRequest = new JSONObject();
                peerRequest.put((Object)"requestType", (Object)"processTransactions");
                peerRequest.put((Object)"transactions", (Object)validTransactionsData);
                Peer.sendToSomePeers(peerRequest);
            }
        }
        
        static void saveTransactions(final String fileName) {
            try (final FileOutputStream fileOutputStream = new FileOutputStream(fileName);
                 final ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                objectOutputStream.writeInt(Nxt.transactionCounter.get());
                objectOutputStream.writeObject(new HashMap(Nxt.transactions));
                objectOutputStream.close();
            }
            catch (IOException e) {
                Nxt.logMessage("Error saving transactions to " + fileName, e);
                throw new RuntimeException(e);
            }
        }
        
        void sign(final String secretPhrase) {
            this.signature = Crypto.sign(this.getBytes(), secretPhrase);
            try {
                while (!this.verify()) {
                    ++this.timestamp;
                    this.signature = new byte[64];
                    this.signature = Crypto.sign(this.getBytes(), secretPhrase);
                }
            }
            catch (RuntimeException e) {
                Nxt.logMessage("Error signing transaction", e);
            }
        }
        
        boolean validateAttachment() {
            if (this.fee > 1000000000L) {
                return false;
            }
            switch (this.type) {
                case 0: {
                    switch (this.subtype) {
                        case 0: {
                            return this.amount > 0 && this.amount < 1000000000L;
                        }
                        default: {
                            return false;
                        }
                    }
                    break;
                }
                case 1: {
                    switch (this.subtype) {
                        case 0: {
                            if (Nxt.lastBlock.get().height < 40000) {
                                return false;
                            }
                            try {
                                final MessagingArbitraryMessageAttachment attachment = (MessagingArbitraryMessageAttachment)this.attachment;
                                return this.amount == 0 && attachment.message.length <= 1000;
                            }
                            catch (RuntimeException e) {
                                Nxt.logDebugMessage("Error validating arbitrary message", e);
                                return false;
                            }
                        }
                        case 1: {
                            if (Nxt.lastBlock.get().height < 22000) {
                                return false;
                            }
                            try {
                                final MessagingAliasAssignmentAttachment attachment2 = (MessagingAliasAssignmentAttachment)this.attachment;
                                if (this.recipient != 1739068987193023818L || this.amount != 0 || attachment2.alias.length() == 0 || attachment2.alias.length() > 100 || attachment2.uri.length() > 1000) {
                                    return false;
                                }
                                final String normalizedAlias = attachment2.alias.toLowerCase();
                                for (int i = 0; i < normalizedAlias.length(); ++i) {
                                    if ("0123456789abcdefghijklmnopqrstuvwxyz".indexOf(normalizedAlias.charAt(i)) < 0) {
                                        return false;
                                    }
                                }
                                final Alias alias = Nxt.aliases.get(normalizedAlias);
                                return alias == null || Arrays.equals(alias.account.publicKey.get(), this.senderPublicKey);
                            }
                            catch (RuntimeException e) {
                                Nxt.logDebugMessage("Error in alias assignment validation", e);
                                return false;
                            }
                            break;
                        }
                    }
                    return false;
                }
                default: {
                    return false;
                }
            }
        }
        
        boolean verify() {
            final Account account = Nxt.accounts.get(this.getSenderAccountId());
            if (account == null) {
                return false;
            }
            final byte[] data = this.getBytes();
            for (int i = 64; i < 128; ++i) {
                data[i] = 0;
            }
            return Crypto.verify(this.signature, data, this.senderPublicKey) && account.setOrVerify(this.senderPublicKey);
        }
        
        public static byte[] calculateTransactionsChecksum() {
            synchronized (Nxt.blocksAndTransactionsLock) {
                final PriorityQueue<Transaction> sortedTransactions = new PriorityQueue<Transaction>(Nxt.transactions.size(), new Comparator<Transaction>() {
                    @Override
                    public int compare(final Transaction o1, final Transaction o2) {
                        final long id1 = o1.getId();
                        final long id2 = o2.getId();
                        return (id1 < id2) ? -1 : ((id1 > id2) ? 1 : ((o1.timestamp < o2.timestamp) ? -1 : ((o1.timestamp > o2.timestamp) ? 1 : 0)));
                    }
                });
                sortedTransactions.addAll(Nxt.transactions.values());
                final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                while (!sortedTransactions.isEmpty()) {
                    digest.update(sortedTransactions.poll().getBytes());
                }
                return digest.digest();
            }
        }
        
        static {
            timestampComparator = new Comparator<Transaction>() {
                @Override
                public int compare(final Transaction o1, final Transaction o2) {
                    return (o1.timestamp < o2.timestamp) ? -1 : ((o1.timestamp > o2.timestamp) ? 1 : 0);
                }
            };
        }
        
        static class MessagingArbitraryMessageAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            final byte[] message;
            
            MessagingArbitraryMessageAttachment(final byte[] message) {
                super();
                this.message = message;
            }
            
            @Override
            public int getSize() {
                return 4 + this.message.length;
            }
            
            @Override
            public byte[] getBytes() {
                final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putInt(this.message.length);
                buffer.put(this.message);
                return buffer.array();
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"message", (Object)Nxt.convert(this.message));
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                return 0L;
            }
        }
        
        static class MessagingAliasAssignmentAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            final String alias;
            final String uri;
            
            MessagingAliasAssignmentAttachment(final String alias, final String uri) {
                super();
                this.alias = alias;
                this.uri = uri;
            }
            
            @Override
            public int getSize() {
                try {
                    return 1 + this.alias.getBytes("UTF-8").length + 2 + this.uri.getBytes("UTF-8").length;
                }
                catch (RuntimeException | UnsupportedEncodingException e) {
                    Nxt.logMessage("Error in getBytes", e);
                    return 0;
                }
            }
            
            @Override
            public byte[] getBytes() {
                try {
                    final byte[] alias = this.alias.getBytes("UTF-8");
                    final byte[] uri = this.uri.getBytes("UTF-8");
                    final ByteBuffer buffer = ByteBuffer.allocate(1 + alias.length + 2 + uri.length);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put((byte)alias.length);
                    buffer.put(alias);
                    buffer.putShort((short)uri.length);
                    buffer.put(uri);
                    return buffer.array();
                }
                catch (RuntimeException | UnsupportedEncodingException e) {
                    Nxt.logMessage("Error in getBytes", e);
                    return null;
                }
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"alias", (Object)this.alias);
                attachment.put((Object)"uri", (Object)this.uri);
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                return 0L;
            }
        }
        
        static class ColoredCoinsAssetIssuanceAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            String name;
            String description;
            int quantity;
            
            ColoredCoinsAssetIssuanceAttachment(final String name, final String description, final int quantity) {
                super();
                this.name = name;
                this.description = ((description == null) ? "" : description);
                this.quantity = quantity;
            }
            
            @Override
            public int getSize() {
                try {
                    return 1 + this.name.getBytes("UTF-8").length + 2 + this.description.getBytes("UTF-8").length + 4;
                }
                catch (RuntimeException | UnsupportedEncodingException e) {
                    Nxt.logMessage("Error in getBytes", e);
                    return 0;
                }
            }
            
            @Override
            public byte[] getBytes() {
                try {
                    final byte[] name = this.name.getBytes("UTF-8");
                    final byte[] description = this.description.getBytes("UTF-8");
                    final ByteBuffer buffer = ByteBuffer.allocate(1 + name.length + 2 + description.length + 4);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put((byte)name.length);
                    buffer.put(name);
                    buffer.putShort((short)description.length);
                    buffer.put(description);
                    buffer.putInt(this.quantity);
                    return buffer.array();
                }
                catch (RuntimeException | UnsupportedEncodingException e) {
                    Nxt.logMessage("Error in getBytes", e);
                    return null;
                }
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"name", (Object)this.name);
                attachment.put((Object)"description", (Object)this.description);
                attachment.put((Object)"quantity", (Object)this.quantity);
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                return 0L;
            }
        }
        
        static class ColoredCoinsAssetTransferAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            long asset;
            int quantity;
            
            ColoredCoinsAssetTransferAttachment(final long asset, final int quantity) {
                super();
                this.asset = asset;
                this.quantity = quantity;
            }
            
            @Override
            public int getSize() {
                return 12;
            }
            
            @Override
            public byte[] getBytes() {
                final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putLong(this.asset);
                buffer.putInt(this.quantity);
                return buffer.array();
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"asset", (Object)Nxt.convert(this.asset));
                attachment.put((Object)"quantity", (Object)this.quantity);
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                return 0L;
            }
        }
        
        static class ColoredCoinsAskOrderPlacementAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            long asset;
            int quantity;
            long price;
            
            ColoredCoinsAskOrderPlacementAttachment(final long asset, final int quantity, final long price) {
                super();
                this.asset = asset;
                this.quantity = quantity;
                this.price = price;
            }
            
            @Override
            public int getSize() {
                return 20;
            }
            
            @Override
            public byte[] getBytes() {
                final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putLong(this.asset);
                buffer.putInt(this.quantity);
                buffer.putLong(this.price);
                return buffer.array();
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"asset", (Object)Nxt.convert(this.asset));
                attachment.put((Object)"quantity", (Object)this.quantity);
                attachment.put((Object)"price", (Object)this.price);
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                return 0L;
            }
        }
        
        static class ColoredCoinsBidOrderPlacementAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            long asset;
            int quantity;
            long price;
            
            ColoredCoinsBidOrderPlacementAttachment(final long asset, final int quantity, final long price) {
                super();
                this.asset = asset;
                this.quantity = quantity;
                this.price = price;
            }
            
            @Override
            public int getSize() {
                return 20;
            }
            
            @Override
            public byte[] getBytes() {
                final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putLong(this.asset);
                buffer.putInt(this.quantity);
                buffer.putLong(this.price);
                return buffer.array();
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"asset", (Object)Nxt.convert(this.asset));
                attachment.put((Object)"quantity", (Object)this.quantity);
                attachment.put((Object)"price", (Object)this.price);
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                return -this.quantity * this.price;
            }
        }
        
        static class ColoredCoinsAskOrderCancellationAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            long order;
            
            ColoredCoinsAskOrderCancellationAttachment(final long order) {
                super();
                this.order = order;
            }
            
            @Override
            public int getSize() {
                return 8;
            }
            
            @Override
            public byte[] getBytes() {
                final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putLong(this.order);
                return buffer.array();
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"order", (Object)Nxt.convert(this.order));
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                return 0L;
            }
        }
        
        static class ColoredCoinsBidOrderCancellationAttachment implements Attachment, Serializable
        {
            static final long serialVersionUID = 0L;
            long order;
            
            ColoredCoinsBidOrderCancellationAttachment(final long order) {
                super();
                this.order = order;
            }
            
            @Override
            public int getSize() {
                return 8;
            }
            
            @Override
            public byte[] getBytes() {
                final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.putLong(this.order);
                return buffer.array();
            }
            
            @Override
            public JSONObject getJSONObject() {
                final JSONObject attachment = new JSONObject();
                attachment.put((Object)"order", (Object)Nxt.convert(this.order));
                return attachment;
            }
            
            @Override
            public long getRecipientDeltaBalance() {
                return 0L;
            }
            
            @Override
            public long getSenderDeltaBalance() {
                final BidOrder bidOrder = Nxt.bidOrders.get(this.order);
                if (bidOrder == null) {
                    return 0L;
                }
                return bidOrder.quantity * bidOrder.price;
            }
        }
        
        interface Attachment
        {
            int getSize();
            
            byte[] getBytes();
            
            JSONObject getJSONObject();
            
            long getRecipientDeltaBalance();
            
            long getSenderDeltaBalance();
        }
    }
    
    static class User
    {
        final ConcurrentLinkedQueue<JSONObject> pendingResponses;
        AsyncContext asyncContext;
        volatile boolean isInactive;
        volatile String secretPhrase;
        volatile byte[] publicKey;
        
        User() {
            super();
            this.pendingResponses = new ConcurrentLinkedQueue<JSONObject>();
        }
        
        void deinitializeKeyPair() {
            this.secretPhrase = null;
            this.publicKey = null;
        }
        
        BigInteger initializeKeyPair(final String secretPhrase) {
            this.publicKey = Crypto.getPublicKey(secretPhrase);
            this.secretPhrase = secretPhrase;
            final byte[] publicKeyHash = Nxt.getMessageDigest("SHA-256").digest(this.publicKey);
            return new BigInteger(1, new byte[] { publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4], publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0] });
        }
        
        void send(final JSONObject response) {
            synchronized (this) {
                if (this.asyncContext == null) {
                    if (this.isInactive) {
                        return;
                    }
                    if (this.pendingResponses.size() > 1000) {
                        this.pendingResponses.clear();
                        this.isInactive = true;
                        if (this.secretPhrase == null) {
                            Nxt.users.values().remove(this);
                        }
                        return;
                    }
                    this.pendingResponses.offer(response);
                }
                else {
                    final JSONArray responses = new JSONArray();
                    JSONObject pendingResponse;
                    while ((pendingResponse = this.pendingResponses.poll()) != null) {
                        responses.add((Object)pendingResponse);
                    }
                    responses.add((Object)response);
                    final JSONObject combinedResponse = new JSONObject();
                    combinedResponse.put((Object)"responses", (Object)responses);
                    this.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                    try (final Writer writer = this.asyncContext.getResponse().getWriter()) {
                        combinedResponse.writeJSONString(writer);
                    }
                    catch (IOException e) {
                        Nxt.logMessage("Error sending response to user", e);
                    }
                    this.asyncContext.complete();
                    this.asyncContext = null;
                }
            }
        }
    }
    
    static class UserAsyncListener implements AsyncListener
    {
        final User user;
        
        UserAsyncListener(final User user) {
            super();
            this.user = user;
        }
        
        public void onComplete(final AsyncEvent asyncEvent) throws IOException {
        }
        
        public void onError(final AsyncEvent asyncEvent) throws IOException {
            synchronized (this.user) {
                this.user.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                try (final Writer writer = this.user.asyncContext.getResponse().getWriter()) {
                    new JSONObject().writeJSONString(writer);
                }
                this.user.asyncContext.complete();
                this.user.asyncContext = null;
            }
        }
        
        public void onStartAsync(final AsyncEvent asyncEvent) throws IOException {
        }
        
        public void onTimeout(final AsyncEvent asyncEvent) throws IOException {
            synchronized (this.user) {
                this.user.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                try (final Writer writer = this.user.asyncContext.getResponse().getWriter()) {
                    new JSONObject().writeJSONString(writer);
                }
                this.user.asyncContext.complete();
                this.user.asyncContext = null;
            }
        }
    }
    
    static class CountingOutputStream extends FilterOutputStream
    {
        private long count;
        
        public CountingOutputStream(final OutputStream out) {
            super(out);
        }
        
        @Override
        public void write(final int b) throws IOException {
            ++this.count;
            super.write(b);
        }
        
        public long getCount() {
            return this.count;
        }
    }
    
    static class CountingInputStream extends FilterInputStream
    {
        private long count;
        
        public CountingInputStream(final InputStream in) {
            super(in);
        }
        
        @Override
        public int read() throws IOException {
            final int read = super.read();
            if (read >= 0) {
                ++this.count;
            }
            return read;
        }
        
        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int read = super.read(b, off, len);
            if (read >= 0) {
                ++this.count;
            }
            return read;
        }
        
        @Override
        public long skip(final long n) throws IOException {
            final long skipped = super.skip(n);
            if (skipped >= 0L) {
                this.count += skipped;
            }
            return skipped;
        }
        
        public long getCount() {
            return this.count;
        }
    }
}
