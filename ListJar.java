import java.io.*;
import java.util.jar.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;

public class ListJar {
    public static void main(String[] args) throws Exception {
        String jarPath = args[0];
        
        try (JarFile jf = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().contains("ProxyType")) {
                    System.out.println("Found: " + entry.getName());
                    byte[] bytes = jf.getInputStream(entry).readAllBytes();
                    // Простой анализ байтов для поиска строк
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    System.out.println("Content: " + content);
                }
            }
        }
    }
}
