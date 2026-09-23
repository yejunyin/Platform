import java.net.URI;
import java.nio.file.*;
import java.io.File;

public class UriTest {
    public static void main(String[] a) throws Exception {
        URI client = new URI("file:///e%3A/%E5%8F%B6%E5%85%B1%E4%BA%AB%E6%96%87%E4%BB%B6/Platform/backend/eb-service-task/src/main/java");
        URI stored = new URI("file:/E:/叶的共享文件/Platform/backend/eb-service-task/src/main/java");
        System.out.println("file.encoding=" + System.getProperty("file.encoding"));
        System.out.println("client -> " + client);
        System.out.println("  Paths.exists: " + Files.exists(Paths.get(client)));
        System.out.println("  new File(URI).exists: " + new File(client).exists());
        System.out.println("stored -> " + stored);
        System.out.println("  Paths.exists: " + Files.exists(Paths.get(stored)));
        System.out.println("  new File(URI).exists: " + new File(stored).exists());
        System.out.println("raw string path E: exists: " + new File("E:\\叶的共享文件\\Platform\\backend\\eb-service-task\\src\\main\\java").exists());
        System.out.println("raw string path e: exists: " + new File("e:\\叶的共享文件\\Platform\\backend\\eb-service-task\\src\\main\\java").exists());
    }
}
