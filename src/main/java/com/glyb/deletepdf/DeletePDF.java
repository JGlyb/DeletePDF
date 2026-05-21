package com.glyb.deletepdf;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class DeletePDF extends JFrame {

    private JButton selectFileButton;
    private JButton exportButton;
    private JPanel checkboxPanel;
    private JScrollPane scrollPane;
    private JLabel statusLabel;
    private File selectedFile;
    private List<JCheckBox> pageCheckboxes;

    public DeletePDF() {
        super("Slet PDF-sider");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 700);
        setMinimumSize(new java.awt.Dimension(500, 400));
        pageCheckboxes = new ArrayList<>();
        initComponents();
        setLocationRelativeTo(null);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectFileButton = new JButton("Vælg PDF fil");
        selectFileButton.addActionListener(e -> selectFile());
        statusLabel = new JLabel("Ingen fil valgt");
        topPanel.add(selectFileButton);
        topPanel.add(statusLabel);
        add(topPanel, BorderLayout.NORTH);

        checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
        scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        exportButton = new JButton("Eksporter PDF uden valgte sider");
        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportPdf());
        bottomPanel.add(exportButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void selectFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF filer", "pdf"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            loadPdfPages();
        }
    }

    private static final int THUMB_HEIGHT = 150;

    private void loadPdfPages() {
        checkboxPanel.removeAll();
        pageCheckboxes.clear();
        exportButton.setEnabled(false);

        int pageCount;
        try (PDDocument document = Loader.loadPDF(selectedFile)) {
            pageCount = document.getNumberOfPages();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Kunne ikke åbne PDF: " + ex.getMessage(),
                    "Fejl", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Ingen fil valgt");
            checkboxPanel.revalidate();
            checkboxPanel.repaint();
            return;
        }

        for (int i = 1; i <= pageCount; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            JCheckBox cb = new JCheckBox("Side " + i);
            JLabel thumbLabel = new JLabel("Indlæser...");
            thumbLabel.setPreferredSize(new java.awt.Dimension(
                    (int) (THUMB_HEIGHT * 0.707), THUMB_HEIGHT));
            thumbLabel.setBorder(BorderFactory.createLineBorder(java.awt.Color.LIGHT_GRAY));
            row.add(cb);
            row.add(thumbLabel);
            pageCheckboxes.add(cb);
            checkboxPanel.add(row);
        }
        statusLabel.setText(selectedFile.getName() + " (" + pageCount + " sider) — renderer thumbnails...");
        exportButton.setEnabled(true);
        checkboxPanel.revalidate();
        checkboxPanel.repaint();

        File fileRef = selectedFile;
        new SwingWorker<Void, ThumbnailResult>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (PDDocument document = Loader.loadPDF(fileRef)) {
                    PDFRenderer renderer = new PDFRenderer(document);
                    for (int i = 0; i < document.getNumberOfPages(); i++) {
                        BufferedImage full = renderer.renderImageWithDPI(i, 36);
                        double scale = (double) THUMB_HEIGHT / full.getHeight();
                        int thumbWidth = (int) (full.getWidth() * scale);
                        BufferedImage thumb = new BufferedImage(thumbWidth, THUMB_HEIGHT, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = thumb.createGraphics();
                        g.drawImage(full, 0, 0, thumbWidth, THUMB_HEIGHT, null);
                        g.dispose();
                        publish(new ThumbnailResult(i, thumb));
                    }
                }
                return null;
            }

            @Override
            protected void process(List<ThumbnailResult> chunks) {
                for (ThumbnailResult r : chunks) {
                    JPanel row = (JPanel) checkboxPanel.getComponent(r.index);
                    JLabel thumbLabel = (JLabel) row.getComponent(1);
                    thumbLabel.setText(null);
                    thumbLabel.setIcon(new ImageIcon(r.image));
                    thumbLabel.setPreferredSize(null);
                }
                checkboxPanel.revalidate();
                checkboxPanel.repaint();
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText(fileRef.getName() + " (" + pageCheckboxes.size() + " sider)");
                } catch (Exception ex) {
                    statusLabel.setText(fileRef.getName() + " — fejl ved thumbnail-rendering");
                }
            }
        }.execute();
    }

    private record ThumbnailResult(int index, BufferedImage image) {}

    private void exportPdf() {
        List<Integer> pagesToRemove = new ArrayList<>();
        for (int i = 0; i < pageCheckboxes.size(); i++) {
            if (pageCheckboxes.get(i).isSelected()) {
                pagesToRemove.add(i);
            }
        }

        if (pagesToRemove.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Ingen sider er valgt til fjernelse.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (pagesToRemove.size() == pageCheckboxes.size()) {
            JOptionPane.showMessageDialog(this,
                    "Alle sider er valgt. Den eksporterede PDF ville være tom.",
                    "Advarsel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String originalName = selectedFile.getName();
        String suggestedName = originalName.replaceFirst("(?i)\\.pdf$", "") + "_modified.pdf";

        JFileChooser saveChooser = new JFileChooser(selectedFile.getParentFile());
        saveChooser.setFileFilter(new FileNameExtensionFilter("PDF filer", "pdf"));
        saveChooser.setSelectedFile(new File(suggestedName));

        if (saveChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File destination = saveChooser.getSelectedFile();
        if (!destination.getName().toLowerCase().endsWith(".pdf")) {
            destination = new File(destination.getAbsolutePath() + ".pdf");
        }

        try (PDDocument document = Loader.loadPDF(selectedFile)) {
            Collections.sort(pagesToRemove, Collections.reverseOrder());
            for (int index : pagesToRemove) {
                document.removePage(index);
            }
            document.save(destination);
            JOptionPane.showMessageDialog(this,
                    "PDF eksporteret til: " + destination.getName(),
                    "Succes", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Kunne ikke gemme PDF: " + ex.getMessage(),
                    "Fejl", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new DeletePDF().setVisible(true);
        });
    }
}
