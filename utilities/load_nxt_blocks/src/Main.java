import java.io.*;
import java.lang.System;
import java.nio.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import org.json.JSONArray;

public class Main {
    public static void main(String[] args) {
        try {
            Downloader downloader = new Downloader("vps3.nxtcrypto.org");
            String jsonBlocksString = downloader.DownloadAllBlocks();

            try (PrintWriter writer = new PrintWriter("jsonBlocks.js", "UTF-8")) {
                writer.print(jsonBlocksString);
            }
        }
        catch (Exception e) {
            System.out.format("Exception encountered: %s", e.toString());
        }
    }

    private static JSONArray LoadBlocksFromFile(String fileName) throws Exception {
        byte[] encoded = Files.readAllBytes(Paths.get("jsonBlocks.js"));
        String contents = Charset.forName("UTF-8").decode(ByteBuffer.wrap(encoded)).toString();
        return new JSONArray(contents);
    }
}
