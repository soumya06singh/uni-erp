package edu.univ.erp.ui;

import edu.univ.erp.data.DBConfig;
import edu.univ.erp.auth.HashUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Main login app - cleaned and role-based redirection to dashboards.
 */
public class MainApp {

    public static void main(String[] args) {
        // Optional: keep system look & feel for native touches
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(MainApp::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("University ERP - Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(520, 420);
        frame.setMinimumSize(new Dimension(480, 360));
        frame.setLocationRelativeTo(null);

        // Root panel with subtle background
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(245, 245, 245)); // light gray
        frame.setContentPane(root);

        // Card (rounded panel)
        RoundedPanel card = new RoundedPanel(18, Color.WHITE);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(22, 28, 22, 28));
        GridBagConstraints c = new GridBagConstraints();

        // Optional logo - put logo.png in resources, or skip if not present
        Image logoImg = loadResourceImage("/logo.png", 64, 64);
        if (logoImg != null) {
            JLabel logoLabel = new JLabel(new ImageIcon(logoImg));
            c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
            c.insets = new Insets(0, 0, 12, 0);
            card.add(logoLabel, c);
        }

        // Title
        JLabel title = new JLabel("Welcome to ERP Login", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(new Color(40, 40, 40));
        c.gridx = 0; c.gridy = 1; c.gridwidth = 2;
        c.insets = new Insets(4, 0, 16, 0);
        card.add(title, c);

        // username label & field
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        userLabel.setForeground(new Color(70, 70, 70));
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1;
        c.insets = new Insets(4, 6, 4, 8);
        c.anchor = GridBagConstraints.LINE_END;
        card.add(userLabel, c);

        JTextField userText = new JTextField(18);
        userText.setFont(new Font("SansSerif", Font.PLAIN, 14));
        userText.setToolTipText("Enter your username");
        userText.setBackground(new Color(250, 253, 255)); // very light pastel
        userText.setBorder(new LineBorder(new Color(220, 220, 220), 1, true));
        userText.setCaretColor(new Color(40, 40, 40));
        c.gridx = 1; c.gridy = 2;
        c.anchor = GridBagConstraints.LINE_START;
        card.add(userText, c);

        // password label & field
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        passwordLabel.setForeground(new Color(70, 70, 70));
        c.gridx = 0; c.gridy = 3; c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(8, 6, 4, 8);
        card.add(passwordLabel, c);

        JPasswordField passwordText = new JPasswordField(18);
        passwordText.setFont(new Font("SansSerif", Font.PLAIN, 14));
        passwordText.setToolTipText("Enter your password");
        passwordText.setBackground(new Color(250, 253, 255));
        passwordText.setBorder(new LineBorder(new Color(220, 220, 220), 1, true));
        passwordText.setCaretColor(new Color(40, 40, 40));
        c.gridx = 1; c.gridy = 3; c.anchor = GridBagConstraints.LINE_START;
        card.add(passwordText, c);

        // small spacer
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        c.insets = new Insets(12, 0, 8, 0);
        card.add(Box.createVerticalStrut(6), c);

        // Login button
        GradientButton loginButton = new GradientButton("Login");
        loginButton.setPreferredSize(new Dimension(240, 44));
        loginButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        loginButton.setToolTipText("Click to sign in");

        loginButton.setEnabled(false);
        DocumentChangeListener.watch(userText, passwordText, enabled -> loginButton.setEnabled(enabled));

        // login action - cleaned and safe
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

                    // update last_login (non-fatal if fails)
                    try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE users_auth SET last_login = ? WHERE user_id = ?")) {
                        updateStmt.setLong(1, System.currentTimeMillis());
                        updateStmt.setString(2, userId);
                        updateStmt.executeUpdate();
                    } catch (Exception uex) {
                        uex.printStackTrace();
                    }

                    // success
                    JOptionPane.showMessageDialog(frame, "Login Successful! Redirecting...", "Success", JOptionPane.INFORMATION_MESSAGE);

                    final String rid = role == null ? "" : role.toUpperCase();
                    final String uid = userId;
                    final String uname = username;

                    // open the dashboard on the EDT
                    SwingUtilities.invokeLater(() -> {
                        switch (rid) {
                            case "INSTRUCTOR" -> {
                                InstructorDashboard dash = new InstructorDashboard(uid, uname);
                                dash.setVisible(true);
                                frame.dispose();
                            }
                            case "STUDENT" -> {
                                StudentDashboard sd = new StudentDashboard(uid, uname);
                                sd.setVisible(true);
                                frame.dispose();
                            }
                            case "ADMIN" -> {
                                AdminDashboard adminDash = new AdminDashboard(uid, uname);
                                adminDash.setVisible(true);
                                frame.dispose();
                            }

                            default -> {
                                JOptionPane.showMessageDialog(null, "Unknown role: " + rid);
                            }
                        }
                    });
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Place button (center aligned)
        c.gridx = 0; c.gridy = 5; c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(10, 0, 0, 0);
        card.add(loginButton, c);

        // bottom small help (register / forgot)
        JLabel help = new JLabel("<html><a href='#'>Forgot password?</a></html>");
        help.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        help.setFont(new Font("SansSerif", Font.PLAIN, 12));
        help.setForeground(new Color(80, 80, 80));
        c.gridx = 0; c.gridy = 6; c.gridwidth = 2;
        c.insets = new Insets(12, 0, 0, 0);
        card.add(help, c);

        // Add the card to center of root
        GridBagConstraints rootC = new GridBagConstraints();
        rootC.gridx = 0; rootC.gridy = 0;
        rootC.anchor = GridBagConstraints.CENTER;
        root.add(card, rootC); // <-- fixed (removed odd cast)

        frame.setVisible(true);
    }

    // helper classes (RoundedPanel, GradientButton, loadResourceImage, DocumentChangeListener)

    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color backgroundColor;

        RoundedPanel(int radius, Color bg) {
            super();
            this.radius = radius;
            this.backgroundColor = bg != null ? bg : getBackground();
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            // paint the rounded background, then allow children to paint
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(backgroundColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static Image loadResourceImage(String path, int w, int h) {
        try (InputStream in = MainApp.class.getResourceAsStream(path)) {
            if (in == null) return null;
            Image img = ImageIO.read(in);
            return img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        } catch (IOException e) {
            return null;
        }
    }

    static class GradientButton extends JButton {

        private Color base = new Color(173, 216, 230);
        private Color base2 = new Color(135, 206, 250);

        private Color hover1 = new Color(160, 200, 220);
        private Color hover2 = new Color(120, 186, 236);

        private boolean hovering = false;

        GradientButton(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setForeground(Color.BLACK);
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            initHover();
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        private void initHover() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            GradientPaint gp;

            if (!hovering) {
                gp = new GradientPaint(0, 0, base, 0, h, base2);
            } else {
                gp = new GradientPaint(0, 0, hover1, 0, h, hover2);
            }

            g2.setPaint(gp);
            g2.fillRoundRect(0, 0, w, h, 15, 15);

            // draw the text / children
            super.paintComponent(g);
            g2.dispose();
        }
    }

    static class DocumentChangeListener {
        static void watch(JTextField a, JPasswordField b,
                          java.util.function.Consumer<Boolean> onChange) {

            javax.swing.event.DocumentListener dl =
                    new javax.swing.event.DocumentListener() {
                        private void update() {
                            boolean enabled = !a.getText().trim().isEmpty()
                                    && b.getPassword().length > 0;
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
