package demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class AppController {

    static final String OLD_MESSAGE = "hello";
    static final String NEW_MESSAGE = "Welcome! This is the new greeting";

    private final AtomicBoolean ready = new AtomicBoolean(true);

    private final AtomicBoolean newGreeting = new AtomicBoolean(false);

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

    @GetMapping("/message")
    public Map<String, Object> message() {
        boolean on = newGreeting.get();
        return Map.of(
                "message", on ? NEW_MESSAGE : OLD_MESSAGE,
                "newGreeting", on,
                "version", version,
                "pod", pod);
    }

    @GetMapping("/admin/feature")
    public Map<String, Object> feature(@RequestParam(required = false) String enabled) {
        if (enabled != null) {
            if (enabled.equals("true")) {
                newGreeting.set(true);
            } else if (enabled.equals("false")) {
                newGreeting.set(false);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "enabled must be 'true' or 'false'");
            }
        }
        return Map.of("feature", "new-greeting", "enabled", newGreeting.get(), "pod", pod);
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