package edu.univ.erp.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class MainApp {

    public static void main(String[] args) {
        // Set the system look and feel for a more native feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set System Look and Feel.");
        }

        SwingUtilities.invokeLater(MainApp::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        // 1. Create the main frame (window)
        JFrame frame = new JFrame("University ERP System - Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setLocationRelativeTo(null); // Center the window

        // 2. Create the panel for the login form
        JPanel loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 5, 8, 5);

        // Title Label
        JLabel titleLabel = new JLabel("Welcome to ERP Login", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginPanel.add(titleLabel, gbc);

        // Username Label
        JLabel userLabel = new JLabel("Username:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        loginPanel.add(userLabel, gbc);

        // Username Text Field
        JTextField userText = new JTextField(15);
        gbc.gridx = 1;
        gbc.gridy = 1;
        loginPanel.add(userText, gbc);

        // Password Label
        JLabel passwordLabel = new JLabel("Password:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        loginPanel.add(passwordLabel, gbc);

        // Password Field
        JPasswordField passwordText = new JPasswordField(15);
        gbc.gridx = 1;
        gbc.gridy = 2;
        loginPanel.add(passwordText, gbc);

        // Login Button
        JButton loginButton = new JButton("Login");
        loginButton.setBackground(new Color(66, 133, 244));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setFont(new Font("Arial", Font.BOLD, 14));

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 5, 5, 5);
        loginPanel.add(loginButton, gbc);

        // --- Login Action Placeholder ---
        loginButton.addActionListener((ActionEvent e) -> {
            String username = userText.getText();
            // This is the Week 1 prototype check.
            JOptionPane.showMessageDialog(frame,
                    "Attempting to log in as: " + username +
                            "\n(Success! Week 1 Prototype Confirmed.)",
                    "Login Attempt",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        // --------------------------------

        // 3. Add the panel to the frame and make it visible
        frame.getContentPane().add(loginPanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }
}