import java.net.URI;
import java.nio.file.*;
import java.io.File;

public class UriTest2 {
    static void t(String s) throws Exception {
        URI u = new URI(s);
        boolean pe = false, fe = false;
        try { pe = Files.exists(Paths.get(u)); } catch (Exception e) { System.out.println("  Paths EX: " + e); }
        try { fe = new File(u).exists(); } catch (Exception e) { System.out.println("  File EX: " + e); }
        System.out.println(s + "\n  Paths=" + pe + " File=" + fe);
    }
    public static void main(String[] a) throws Exception {
        t("file:///e%3A/Windows");                       // 1 encoded lower drive, ascii
        t("file:///E%3A/Windows");                       // 2 encoded upper drive, ascii
        t("file:///e:/Windows");                         // 3 raw lower drive
        t("file:///e%3A/%E5%8F%B6%E7%9A%84%E5%85%B1%E4%BA%AB%E6%96%87%E4%BB%B6/Platform");  // 4 encoded lower + encoded chinese
        t("file:///E%3A/%E5%8F%B6%E7%9A%84%E5%85%B1%E4%BA%AB%E6%96%87%E4%BB%B6/Platform");  // 5 encoded upper + encoded chinese
        t("file:///e:/叶的共享文件/Platform");             // 6 raw lower + raw chinese
        // what the IDE actually sends for the java source root:
        t("file:///e%3A/%E5%8F%B6%E7%9A%84%E5%85%B1%E4%BA%AB%E6%96%87%E4%BB%B6/Platform/backend/eb-service-task/src/main/java");
    }
}
