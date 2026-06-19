package com.buddyai.agent.ui;

import com.buddyai.agent.repository.ClientAuthRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles the UI login/logout lifecycle.
 *
 * <p>Authentication is deliberately kept simple: the user supplies a
 * {@code clientId} and {@code secretKey}, which are validated against the
 * {@code client_auth} table. On success a flag is written to the HTTP
 * session; on failure the user is redirected back to the login page with
 * a flash error message.
 *
 * <p>Session invalidation on logout ensures that all session-scoped
 * attributes are cleared.
 */
@Controller
@RequestMapping("/ui")
@Slf4j
@RequiredArgsConstructor
public class LoginController {

    private final ClientAuthRepository clientAuthRepository;

    // -------------------------------------------------------------------------
    // GET /ui/login
    // -------------------------------------------------------------------------

    /**
     * Render the login page.
     */
    @GetMapping("/login")
    public String loginPage() {
        return "pages/login";
    }

    // -------------------------------------------------------------------------
    // POST /ui/login
    // -------------------------------------------------------------------------

    /**
     * Process login form submission.
     *
     * <p>If the supplied credentials match an <em>enabled</em> client record,
     * the session is marked as authenticated and the user is forwarded to the
     * dashboard. Otherwise the user is redirected back to the login page with
     * a flash error attribute that Thymeleaf can display.
     *
     * @param clientId      submitted client identifier
     * @param secretKey     submitted secret key
     * @param session       the current HTTP session
     * @param redirectAttrs used to pass flash attributes through the redirect
     * @return redirect target
     */
    @PostMapping("/login")
    public String login(
            @RequestParam String clientId,
            @RequestParam String secretKey,
            HttpSession session,
            RedirectAttributes redirectAttrs) {

        var auth = clientAuthRepository.findByClientIdAndEnabled(clientId, true);
        if (auth.isPresent() && auth.get().getSecretKey().equals(secretKey)) {
            session.setAttribute("clientId", clientId);
            session.setAttribute("authenticated", true);
            log.info("UI login successful for clientId={}", clientId);
            return "redirect:/ui/dashboard";
        }

        log.warn("UI login failed for clientId={}", clientId);
        redirectAttrs.addFlashAttribute("errorMsg", "Invalid Client ID or Secret Key");
        return "redirect:/ui/login";
    }

    // -------------------------------------------------------------------------
    // GET /ui/logout
    // -------------------------------------------------------------------------

    /**
     * Invalidate the current session and redirect to the login page.
     *
     * @param session the session to invalidate
     * @return redirect to the login page
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        String clientId = (String) session.getAttribute("clientId");
        session.invalidate();
        log.info("UI logout for clientId={}", clientId);
        return "redirect:/ui/login";
    }
}
