package edu.univ.erp.ui;
import edu.univ.erp.data.DBConfig;
import edu.univ.erp.auth.HashUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MainApp {

    private static final String[] RESOURCE_CANDIDATES = new String[]{ "login_bg.jpg", "iiit.png" };
    private static final String FALLBACK_IMAGE_PATH = "src/main/resources/iiit.png";
    private static final Color ACCENT = new Color(0, 180, 180);
    private static final Color ACCENT_HOVER = new Color(0, 150, 150);
    private static BufferedImage heroOriginal = null;

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        heroOriginal = loadHeroOriginal();
        SwingUtilities.invokeLater(MainApp::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("University ERP - Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);

        JLayeredPane layered = new JLayeredPane();
        frame.setContentPane(layered);

        JLabel bgLabel = new JLabel();
        bgLabel.setBounds(0, 0, frame.getWidth(), frame.getHeight());
        layered.add(bgLabel, Integer.valueOf(0));

        JPanel overlay = new JPanel();
        overlay.setOpaque(false);
        overlay.setBounds(0, 0, frame.getWidth(), frame.getHeight());
        layered.add(overlay, Integer.valueOf(1));

        RoundedPanel glass = new RoundedPanel(14, new Color(255, 255, 255, 230)); // a bit translucent
        glass.setLayout(new GridBagLayout());
        glass.setBorder(new EmptyBorder(28, 36, 28, 36));
        int cardW = 420;
        int cardH = 380;
        glass.setSize(cardW, cardH);
        layered.add(glass, Integer.valueOf(2));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0;

        JLabel title = new JLabel("ERP LOGIN");
        title.setFont(new Font("Segoe UI", Font.BOLD, 25));
        title.setForeground(new Color(6, 150, 140));
        glass.add(title, c);

        c.gridy = 1; c.insets = new Insets(8, 0, 16, 0);
        JLabel subtitle = new JLabel("Sign in");
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        subtitle.setForeground(new Color(110, 115, 120));
        glass.add(subtitle, c);

        c.gridy = 2; c.insets = new Insets(12, 0, 6, 0);
        glass.add(new JLabel("Username"), c);

        c.gridy = 3; c.insets = new Insets(0, 0, 8, 0);
        JTextField userText = new JTextField();
        userText.setPreferredSize(new Dimension(260, 28));
        userText.setBorder(new LineBorder(new Color(220,220,220), 1, true));
        glass.add(userText, c);

        c.gridy = 4; c.insets = new Insets(10, 0, 6, 0);
        glass.add(new JLabel("Password"), c);

        c.gridy = 5; c.insets = new Insets(0, 0, 12, 0);
        JPasswordField passwordText = new JPasswordField();
        passwordText.setPreferredSize(new Dimension(260, 28));
        passwordText.setBorder(new LineBorder(new Color(220,220,220), 1, true));
        glass.add(passwordText, c);

        c.gridy = 6; c.insets = new Insets(10, 0, 6, 0);
        PillButton loginButton = new PillButton("Login"); // Changed class name
        loginButton.setPreferredSize(new Dimension(260, 44));
        loginButton.setEnabled(false);
        glass.add(loginButton, c);

        DocumentChangeListener.watch(userText, passwordText, enabled -> loginButton.setEnabled(enabled));

        loginButton.addActionListener((ActionEvent e) -> {
            String username = userText.getText().trim();
            String password = new String(passwordText.getPassword());

            String sql = "SELECT user_id, username, role, password_hash FROM users_auth WHERE username = ?";
            try (Connection conn = DBConfig.getAuthConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(frame, "Invalid username!", "Login Failed", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    String userId = rs.getString("user_id");
                    String storedHash = rs.getString("password_hash");
                    String role = rs.getString("role");

                    if (!HashUtil.checkPassword(password, storedHash)) {
                        JOptionPane.showMessageDialog(frame, "Incorrect password!", "Login Failed", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE users_auth SET last_login = ? WHERE user_id = ?")) {
                        updateStmt.setLong(1, System.currentTimeMillis());
                        updateStmt.setString(2, userId);
                        updateStmt.executeUpdate();
                    } catch (Exception uex) {
                        uex.printStackTrace();
                    }

                    JOptionPane.showMessageDialog(frame, "Login Successful! Redirecting...", "Success", JOptionPane.INFORMATION_MESSAGE);

                    final String rid = role == null ? "" : role.toUpperCase();
                    final String uid = userId;
                    final String uname = username;

                    SwingUtilities.invokeLater(() -> {
                        switch (rid) {
                            case "INSTRUCTOR" -> {
                                InstructorDashboard dash = new InstructorDashboard(uid, uname);
                                dash.setVisible(true);
                            }
                            case "STUDENT" -> {
                                StudentDashboard sd = new StudentDashboard(uid, uname);
                                sd.setVisible(true);
                            }
                            case "ADMIN" -> {
                                AdminDashboard adminDash = new AdminDashboard(uid, uname);
                                adminDash.setVisible(true);
                            }
                            default -> JOptionPane.showMessageDialog(null, "Unknown role: " + rid);
                        }
                        frame.dispose();
                    });
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int fw = frame.getContentPane().getWidth();
                int fh = frame.getContentPane().getHeight();

                bgLabel.setBounds(0, 0, fw, fh);
                overlay.setBounds(0, 0, fw, fh);

                int gx = Math.max(40, (fw - glass.getWidth()) / 2);
                int gy = Math.max(40, (fh - glass.getHeight()) / 2);
                glass.setLocation(gx, gy);

                BufferedImage cover = makeBlurredCover(heroOriginal, fw, fh, 14); // blur radius 14
                if (cover != null) bgLabel.setIcon(new ImageIcon(cover));
                else bgLabel.setIcon(null);
            }
        });

        SwingUtilities.invokeLater(() -> {
            int fw = frame.getWidth();
            int fh = frame.getHeight();
            bgLabel.setBounds(0, 0, fw, fh);
            overlay.setBounds(0, 0, fw, fh);
            int gx = Math.max(40, (fw - glass.getWidth()) / 2);
            int gy = Math.max(40, (fh - glass.getHeight()) / 2);
            glass.setLocation(gx, gy);

            BufferedImage cover = makeBlurredCover(heroOriginal, fw, fh, 14);
            if (cover != null) bgLabel.setIcon(new ImageIcon(cover));
        });

        frame.setVisible(true);
    }


    private static BufferedImage loadHeroOriginal() {
        ClassLoader cl = MainApp.class.getClassLoader();
        for (String cand : RESOURCE_CANDIDATES) {
            try (InputStream in = cl.getResourceAsStream(cand)) {
                if (in != null) {
                    try { return ImageIO.read(in); } catch (Exception ex) { ex.printStackTrace(); }
                }
            } catch (Exception ignored) {}
        }
        for (String cand : RESOURCE_CANDIDATES) {
            String p = cand.startsWith("/") ? cand : "/" + cand;
            try (InputStream in = MainApp.class.getResourceAsStream(p)) {
                if (in != null) {
                    try { return ImageIO.read(in); } catch (Exception ex) { ex.printStackTrace(); }
                }
            } catch (Exception ignored) {}
        }
        try {
            File f = new File(FALLBACK_IMAGE_PATH);
            if (f.exists()) return ImageIO.read(f);
        } catch (Exception ignored) {}
        return null;
    }

    private static BufferedImage makeBlurredCover(BufferedImage src, int targetW, int targetH, int blurRadius) {
        if (src == null || targetW <= 0 || targetH <= 0) return null;

        double scaleX = (double) targetW / src.getWidth();
        double scaleY = (double) targetH / src.getHeight();
        double scale = Math.max(scaleX, scaleY); // cover

        int scaledW = (int) Math.round(src.getWidth() * scale);
        int scaledH = (int) Math.round(src.getHeight() * scale);

        Image tmp = src.getScaledInstance(scaledW, scaledH, Image.SCALE_SMOOTH);
        BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(tmp, 0, 0, null);
        g.dispose();

        int x = (scaledW - targetW) / 2;
        int y = (scaledH - targetH) / 2;
        BufferedImage cropped = scaled.getSubimage(x, y, targetW, targetH);

        BufferedImage blurred = applyGaussianBlur(cropped, blurRadius);

        return applyTintAndDesaturate(blurred, 0.20f, new Color(0, 0, 0, 110));
    }


    private static BufferedImage applyGaussianBlur(BufferedImage img, int radius) {
        if (radius < 1) return img;
        int w = img.getWidth();
        int h = img.getHeight();
        int[] inPixels = img.getRGB(0, 0, w, h, null, 0, w);
        int[] temp = new int[inPixels.length];
        int[] outPixels = new int[inPixels.length];

        int kernelSize = radius * 2 + 1;

        for (int y = 0; y < h; y++) {
            int yw = y * w;
            for (int x = 0; x < w; x++) {
                long r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int px = x + k;
                    if (px < 0) px = 0;
                    else if (px >= w) px = w - 1;
                    int rgb = inPixels[yw + px];
                    r += (rgb >> 16) & 0xFF;
                    g += (rgb >> 8) & 0xFF;
                    b += rgb & 0xFF;
                }
                int rr = (int) (r / kernelSize);
                int gg = (int) (g / kernelSize);
                int bb = (int) (b / kernelSize);
                temp[yw + x] = (0xFF << 24) | (rr << 16) | (gg << 8) | bb;
            }
        }

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                long r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int py = y + k;
                    if (py < 0) py = 0;
                    else if (py >= h) py = h - 1;
                    int rgb = temp[py * w + x];
                    r += (rgb >> 16) & 0xFF;
                    g += (rgb >> 8) & 0xFF;
                    b += rgb & 0xFF;
                }
                int rr = (int) (r / kernelSize);
                int gg = (int) (g / kernelSize);
                int bb = (int) (b / kernelSize);
                outPixels[y * w + x] = (0xFF << 24) | (rr << 16) | (gg << 8) | bb;
            }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, outPixels, 0, w);
        return out;
    }


    private static BufferedImage applyTintAndDesaturate(BufferedImage src, float desaturateAmount, Color overlay) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);

        // simple desaturation per-pixel (cheap)
        if (desaturateAmount > 0f) {
            int[] pixels = out.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i];
                int r = (argb >> 16) & 0xFF;
                int gcol = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // luminance
                int lum = (int)(0.2126*r + 0.7152*gcol + 0.0722*b);
                int nr = (int)(r*(1-desaturateAmount) + lum*desaturateAmount);
                int ng = (int)(gcol*(1-desaturateAmount) + lum*desaturateAmount);
                int nb = (int)(b*(1-desaturateAmount) + lum*desaturateAmount);
                pixels[i] = (0xFF<<24) | (nr<<16) | (ng<<8) | nb;
            }
            out.setRGB(0,0,w,h,pixels,0,w);
        }

        if (overlay != null) {
            g.setColor(overlay);
            g.fillRect(0, 0, w, h);
        }
        g.dispose();
        return out;
    }


    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bg;

        RoundedPanel(int radius, Color bg) {
            super();
            this.radius = radius;
            this.bg = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class PillButton extends JButton {
        public PillButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setFont(new Font("Segoe UI", Font.BOLD, 16)); // 14 looks better for main login
            setForeground(Color.WHITE);
            setBackground(ACCENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(140, 40));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if(isEnabled()) setBackground(ACCENT_HOVER); }
                @Override public void mouseExited(MouseEvent e) { setBackground(ACCENT); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!isEnabled()) {
                g.setColor(new Color(200, 200, 200));
            } else if (getModel().isPressed()) {
                g.setColor(getBackground().darker());
            } else {
                g.setColor(getBackground());
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
            g2.dispose();

            super.paintComponent(g);
        }
    }
    static class DocumentChangeListener {
        static void watch(JTextField a, JPasswordField b, java.util.function.Consumer<Boolean> onChange) {
            javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
                private void update() {
                    boolean enabled = !a.getText().trim().isEmpty() && b.getPassword().length > 0;
                    onChange.accept(enabled);
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
            };
            a.getDocument().addDocumentListener(dl);
            b.getDocument().addDocumentListener(dl);
        }
    }
}
