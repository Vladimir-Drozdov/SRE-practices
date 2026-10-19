package demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class AppController {

    private final AtomicBoolean ready = new AtomicBoolean(true);
    private final String version;
    private final String pod;

    public AppController(@Value("${app.version}") String version) throws Exception {
        this.version = version;
        this.pod = InetAddress.getLocalHost().getHostName();
    }

    @GetMapping("/")
    public Map<String, String> hello() {
        return Map.of("message", "hello", "version", version, "pod", pod);
    }

    @GetMapping("/live")
    public ResponseEntity<String> live() {
        return ResponseEntity.ok("alive");
    }

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ready.get()
                ? ResponseEntity.ok("ready")
                : ResponseEntity.status(503).body("not ready");
    }

    @PostMapping("/unready")
    public ResponseEntity<String> unready() {
        ready.set(false);
        return ResponseEntity.ok("flag=false");
    }

    @PostMapping("/ready-on")
    public ResponseEntity<String> readyOn() {
        ready.set(true);
        return ResponseEntity.ok("flag=true");
    }
}