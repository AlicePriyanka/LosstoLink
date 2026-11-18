package com.deepspace.analytics.service;

import com.deepspace.analytics.model.AnalysisDashboardView.DashboardCharts;
import com.deepspace.analytics.model.AnalysisDashboardView.GeometricChartPoint;
import com.deepspace.analytics.model.AnalysisDashboardView.PoissonChartPoint;
import com.deepspace.analytics.model.AnalysisDashboardView.ReliabilityChartPoint;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Validates browser-captured Chart.js PNG snapshots or renders crisp
 * high-resolution white-background academic Java2D charts directly from
 * {@link DashboardCharts} data produced by
 * {@link PoissonService} and {@link GeometricService}.
 */
@Service
public class ReportChartImageService {

    private static final String PNG_DATA_URI_PREFIX = "data:image/png;base64,";
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int MAX_ENCODED_DATA_URI_LENGTH = 2_800_000;
    private static final int MAX_IMAGE_DIMENSION = 3200;

    private static final int CORE_CHART_WIDTH = 1120;
    private static final int CORE_CHART_HEIGHT = 400;

    private static final Color BG_WHITE = Color.WHITE;
    private static final Color PANEL_BORDER = new Color(203, 213, 225);
    private static final Color GRID_LINE = new Color(226, 232, 240);
    private static final Color AXIS_LINE = new Color(100, 116, 139);
    private static final Color TEXT_NAVY = new Color(15, 23, 42);
    private static final Color TEXT_SLATE = new Color(71, 85, 105);

    private static final Color BLUE_BAR_DEFAULT = new Color(147, 197, 253, 210);
    private static final Color BLUE_BAR_BORDER = new Color(37, 99, 235);
    private static final Color BLUE_BAR_HIGHLIGHT = new Color(29, 78, 216);

    private static final Color GREEN_BAR_DEFAULT = new Color(110, 231, 183, 200);
    private static final Color GREEN_BAR_BORDER = new Color(5, 150, 105);
    private static final Color GREEN_BAR_HIGHLIGHT = new Color(4, 120, 87);

    private static final Color INDIGO_LINE = new Color(79, 70, 229);
    private static final Color INDIGO_FILL_TOP = new Color(79, 70, 229, 64);
    private static final Color INDIGO_FILL_BOTTOM = new Color(79, 70, 229, 8);

    /**
     * Resolves a validated Chart.js PNG snapshot if supplied and valid; otherwise
     * renders the Poisson probability distribution chart directly from Java results.
     */
    public byte[] resolvePoissonChartPng(String dataUri, DashboardCharts charts) {
        byte[] decoded = decodeValidatedPngDataUri(dataUri);
        return (decoded != null) ? decoded : renderPoissonChart(charts);
    }

    /**
     * Resolves a validated Chart.js PNG snapshot if supplied and valid; otherwise
     * renders the Geometric first-success distribution chart directly from Java results.
     */
    public byte[] resolveGeometricChartPng(String dataUri, DashboardCharts charts) {
        byte[] decoded = decodeValidatedPngDataUri(dataUri);
        return (decoded != null) ? decoded : renderGeometricChart(charts);
    }

    /**
     * Resolves a validated Chart.js PNG snapshot if supplied and valid; otherwise
     * renders the Cumulative Communication Reliability chart directly from Java results.
     */
    public byte[] resolveReliabilityChartPng(String dataUri, DashboardCharts charts) {
        byte[] decoded = decodeValidatedPngDataUri(dataUri);
        return (decoded != null) ? decoded : renderReliabilityChart(charts);
    }

    /**
     * Strictly validates a {@code data:image/png;base64,...} URI before it is
     * embedded into the PDF document.
     */
    public byte[] decodeValidatedPngDataUri(String dataUri) {
        if (dataUri == null || dataUri.isEmpty()) {
            return null;
        }
        String trimmed = dataUri.trim();
        if (trimmed.length() > MAX_ENCODED_DATA_URI_LENGTH || !trimmed.startsWith(PNG_DATA_URI_PREFIX)) {
            return null;
        }
        String base64Payload = trimmed.substring(PNG_DATA_URI_PREFIX.length());
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (decoded.length < PNG_MAGIC.length) {
            return null;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (decoded[i] != PNG_MAGIC[i]) {
                return null;
            }
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(decoded));
            if (img == null
                    || img.getWidth() < 80
                    || img.getHeight() < 80
                    || img.getWidth() > MAX_IMAGE_DIMENSION
                    || img.getHeight() > MAX_IMAGE_DIMENSION) {
                return null;
            }
            return decoded;
        } catch (IOException ex) {
            return null;
        }
    }

    public byte[] renderPoissonChart(DashboardCharts charts) {
        if (charts == null || charts.getPoissonSeries() == null || charts.getPoissonSeries().isEmpty()) {
            throw new IllegalArgumentException("Poisson chart series is not available for this analysis mode.");
        }
        List<PoissonChartPoint> series = charts.getPoissonSeries();
        BufferedImage image = createCanvas(CORE_CHART_WIDTH, CORE_CHART_HEIGHT);
        Graphics2D g = prepareGraphics(image, CORE_CHART_WIDTH, CORE_CHART_HEIGHT);

        int padLeft = 96;
        int padRight = 32;
        int padTop = 58;
        int padBottom = 64;
        int plotW = CORE_CHART_WIDTH - padLeft - padRight;
        int plotH = CORE_CHART_HEIGHT - padTop - padBottom;

        drawLegendBadge(g, CORE_CHART_WIDTH / 2 - 260, 14, BLUE_BAR_DEFAULT, BLUE_BAR_BORDER,
                "Poisson P(X = k) (\u03BB = " + charts.getLambdaDisplay() + ")");
        drawLegendBadge(g, CORE_CHART_WIDTH / 2 + 45, 14, BLUE_BAR_HIGHLIGHT, TEXT_NAVY,
                "Highlighted Selected k = " + charts.getSelectedDropCount());

        double maxVal = 0.0;
        for (PoissonChartPoint pt : series) {
            if (Double.isFinite(pt.getProbability())) {
                maxVal = Math.max(maxVal, pt.getProbability());
            }
        }
        double yMax = computeProbabilityAxisMax(maxVal, 0.30);

        drawYAxisGrid(g, padLeft, padTop, plotW, plotH, yMax, "Probability P(X = k)");

        int count = Math.max(1, series.size());
        double slotW = (double) plotW / count;
        int barW = Math.max(8, (int) Math.round(slotW * 0.64));
        int step = computeLabelStep(count, 16);

        for (int i = 0; i < count; i++) {
            PoissonChartPoint pt = series.get(i);
            int centerX = (int) Math.round(padLeft + (i + 0.5) * slotW);
            int barX = centerX - barW / 2;
            double prob = Double.isFinite(pt.getProbability()) ? pt.getProbability() : 0.0;
            int barH = Math.max(1, (int) Math.round((prob / yMax) * plotH));
            int barY = padTop + plotH - barH;

            g.setColor(pt.isSelected() ? BLUE_BAR_HIGHLIGHT : BLUE_BAR_DEFAULT);
            g.fill(new RoundRectangle2D.Double(barX, barY, barW, barH, 6, 6));
            g.setStroke(new BasicStroke(pt.isSelected() ? 2.2f : 1.2f));
            g.setColor(pt.isSelected() ? TEXT_NAVY : BLUE_BAR_BORDER);
            g.draw(new RoundRectangle2D.Double(barX, barY, barW, barH, 6, 6));

            if (pt.isSelected()) {
                drawHighlightCallout(g, centerX, padLeft, padLeft + plotW, padTop, padTop + plotH, barY,
                        "Selected k = " + charts.getSelectedDropCount(), BLUE_BAR_HIGHLIGHT);
            }
            if (i % step == 0 || pt.isSelected()) {
                drawCenteredTick(g, String.valueOf(pt.getDropCount()), centerX, padTop + plotH + 20);
            }
        }

        drawXAxisLabel(g, padLeft, plotW, CORE_CHART_HEIGHT - 16, "Number of Transmission Drops (k)");
        g.dispose();
        return encodePng(image);
    }

    public byte[] renderGeometricChart(DashboardCharts charts) {
        if (charts == null || charts.getGeometricSeries() == null || charts.getGeometricSeries().isEmpty()) {
            throw new IllegalArgumentException("Geometric chart series is not available for this analysis mode.");
        }
        List<GeometricChartPoint> series = charts.getGeometricSeries();
        BufferedImage image = createCanvas(CORE_CHART_WIDTH, CORE_CHART_HEIGHT);
        Graphics2D g = prepareGraphics(image, CORE_CHART_WIDTH, CORE_CHART_HEIGHT);

        int padLeft = 96;
        int padRight = 32;
        int padTop = 58;
        int padBottom = 64;
        int plotW = CORE_CHART_WIDTH - padLeft - padRight;
        int plotH = CORE_CHART_HEIGHT - padTop - padBottom;

        drawLegendBadge(g, CORE_CHART_WIDTH / 2 - 260, 14, GREEN_BAR_DEFAULT, GREEN_BAR_BORDER,
                "Geometric P(Y = k) (p = " + charts.getSuccessProbabilityDisplay() + ")");
        drawLegendBadge(g, CORE_CHART_WIDTH / 2 + 45, 14, GREEN_BAR_HIGHLIGHT, TEXT_NAVY,
                "Most Likely: Attempt " + charts.getMostLikelyAttempt());

        double maxVal = 0.0;
        for (GeometricChartPoint pt : series) {
            if (Double.isFinite(pt.getProbability())) {
                maxVal = Math.max(maxVal, pt.getProbability());
            }
        }
        double yMax = Math.min(1.05, computeProbabilityAxisMax(maxVal, 0.25));

        drawYAxisGrid(g, padLeft, padTop, plotW, plotH, yMax, "First Success Prob. P(Y = k)");

        int count = Math.max(1, series.size());
        double slotW = (double) plotW / count;
        int barW = Math.max(8, Math.min(96, (int) Math.round(slotW * 0.62)));
        int step = computeLabelStep(count, 12);

        for (int i = 0; i < count; i++) {
            GeometricChartPoint pt = series.get(i);
            int centerX = (int) Math.round(padLeft + (i + 0.5) * slotW);
            int barX = centerX - barW / 2;
            double prob = Double.isFinite(pt.getProbability()) ? pt.getProbability() : 0.0;
            int barH = Math.max(1, (int) Math.round((prob / yMax) * plotH));
            int barY = padTop + plotH - barH;

            g.setColor(pt.isMostLikely() ? GREEN_BAR_HIGHLIGHT : GREEN_BAR_DEFAULT);
            g.fill(new RoundRectangle2D.Double(barX, barY, barW, barH, 6, 6));
            g.setStroke(new BasicStroke(pt.isMostLikely() ? 2.2f : 1.2f));
            g.setColor(pt.isMostLikely() ? TEXT_NAVY : GREEN_BAR_BORDER);
            g.draw(new RoundRectangle2D.Double(barX, barY, barW, barH, 6, 6));

            if (pt.isMostLikely()) {
                drawHighlightCallout(g, centerX, padLeft, padLeft + plotW, padTop, padTop + plotH, barY,
                        "Most Likely: Attempt " + charts.getMostLikelyAttempt(), GREEN_BAR_HIGHLIGHT);
            }
            if (i % step == 0 || pt.isMostLikely()) {
                String tick = count > 16 ? String.valueOf(pt.getAttempt()) : ("Attempt " + pt.getAttempt());
                drawCenteredTick(g, tick, centerX, padTop + plotH + 20);
            }
        }

        drawXAxisLabel(g, padLeft, plotW, CORE_CHART_HEIGHT - 16, "Transmission Attempt Number");
        g.dispose();
        return encodePng(image);
    }

    public byte[] renderReliabilityChart(DashboardCharts charts) {
        if (charts == null || charts.getReliabilitySeries() == null || charts.getReliabilitySeries().isEmpty()) {
            throw new IllegalArgumentException("Cumulative reliability chart series is not available for this analysis mode.");
        }
        List<ReliabilityChartPoint> series = charts.getReliabilitySeries();
        BufferedImage image = createCanvas(CORE_CHART_WIDTH, CORE_CHART_HEIGHT);
        Graphics2D g = prepareGraphics(image, CORE_CHART_WIDTH, CORE_CHART_HEIGHT);

        int padLeft = 96;
        int padRight = 32;
        int padTop = 58;
        int padBottom = 64;
        int plotW = CORE_CHART_WIDTH - padLeft - padRight;
        int plotH = CORE_CHART_HEIGHT - padTop - padBottom;

        drawLegendBadge(g, CORE_CHART_WIDTH / 2 - 260, 14, new Color(199, 210, 254), INDIGO_LINE,
                "Cumulative Reliability R(k) = 1 \u2212 (1 \u2212 p)^k");
        drawLegendBadge(g, CORE_CHART_WIDTH / 2 + 45, 14, INDIGO_LINE, TEXT_NAVY,
                "Selected Max Attempt n = " + charts.getSelectedMaxAttempts());

        double maxRelVal = 0.0;
        for (ReliabilityChartPoint pt : series) {
            if (Double.isFinite(pt.getCumulativeProbability())) {
                maxRelVal = Math.max(maxRelVal, pt.getCumulativeProbability());
            }
        }
        double yMax = (maxRelVal > 0.0 && maxRelVal < 0.08)
                ? computeProbabilityAxisMax(maxRelVal, maxRelVal)
                : 1.0;
        drawYAxisGrid(g, padLeft, padTop, plotW, plotH, yMax, "Cumulative Reliability R(n)");

        int count = Math.max(1, series.size());
        double[] xs = new double[count];
        double[] ys = new double[count];
        for (int i = 0; i < count; i++) {
            ReliabilityChartPoint pt = series.get(i);
            xs[i] = (count == 1)
                    ? padLeft + plotW / 2.0
                    : padLeft + (i * (double) plotW / (count - 1));
            double cumVal = Double.isFinite(pt.getCumulativeProbability()) ? pt.getCumulativeProbability() : 0.0;
            double ratio = Math.min(1.0, Math.max(0.0, cumVal / yMax));
            ys[i] = padTop + plotH - (ratio * plotH);
        }

        // Shaded area fill beneath curve
        if (count >= 2) {
            Path2D area = new Path2D.Double();
            area.moveTo(xs[0], padTop + plotH);
            for (int i = 0; i < count; i++) {
                area.lineTo(xs[i], ys[i]);
            }
            area.lineTo(xs[count - 1], padTop + plotH);
            area.closePath();
            g.setPaint(new GradientPaint(0, padTop, INDIGO_FILL_TOP, 0, padTop + plotH, INDIGO_FILL_BOTTOM));
            g.fill(area);

            Path2D line = new Path2D.Double();
            line.moveTo(xs[0], ys[0]);
            for (int i = 1; i < count; i++) {
                line.lineTo(xs[i], ys[i]);
            }
            g.setColor(INDIGO_LINE);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
        }

        int step = computeLabelStep(count, 14);
        for (int i = 0; i < count; i++) {
            ReliabilityChartPoint pt = series.get(i);
            boolean selected = pt.isSelectedMaxAttempt();
            double r = selected ? 6.5 : 4.2;
            g.setColor(selected ? Color.WHITE : INDIGO_LINE);
            g.fill(new Ellipse2D.Double(xs[i] - r, ys[i] - r, r * 2, r * 2));
            g.setColor(selected ? INDIGO_LINE : new Color(49, 46, 129));
            g.setStroke(new BasicStroke(selected ? 2.6f : 1.4f));
            g.draw(new Ellipse2D.Double(xs[i] - r, ys[i] - r, r * 2, r * 2));

            if (selected) {
                drawHighlightCallout(g, (int) Math.round(xs[i]), padLeft, padLeft + plotW, padTop, padTop + plotH,
                        (int) Math.round(ys[i]), "Selected n = " + charts.getSelectedMaxAttempts(), INDIGO_LINE);
            }
            if (i % step == 0 || selected) {
                drawCenteredTick(g, String.valueOf(pt.getAttempt()), (int) Math.round(xs[i]), padTop + plotH + 20);
            }
        }

        drawXAxisLabel(g, padLeft, plotW, CORE_CHART_HEIGHT - 16, "Number of Attempts");
        g.dispose();
        return encodePng(image);
    }

    private BufferedImage createCanvas(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    private Graphics2D prepareGraphics(BufferedImage image, int width, int height) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(BG_WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(1.4f));
        g.drawRect(0, 0, width - 1, height - 1);
        return g;
    }

    private void drawLegendBadge(Graphics2D g, int x, int y, Color fill, Color border, String label) {
        g.setColor(fill);
        g.fillRoundRect(x, y + 2, 14, 14, 3, 3);
        g.setColor(border);
        g.setStroke(new BasicStroke(1.3f));
        g.drawRoundRect(x, y + 2, 14, 14, 3, 3);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(TEXT_NAVY);
        g.drawString(label, x + 22, y + 14);
    }

    private void drawYAxisGrid(Graphics2D g, int padLeft, int padTop, int plotW, int plotH, double yMax, String yTitle) {
        int ticks = 5;
        Font tickFont = new Font(Font.MONOSPACED, Font.PLAIN, 11);
        g.setFont(tickFont);
        FontMetrics fm = g.getFontMetrics();

        for (int i = 0; i <= ticks; i++) {
            double ratio = (double) i / ticks;
            double value = yMax * ratio;
            int y = (int) Math.round(padTop + plotH - ratio * plotH);

            g.setColor(i == 0 ? AXIS_LINE : GRID_LINE);
            g.setStroke(new BasicStroke(i == 0 ? 1.4f : 1.0f));
            g.drawLine(padLeft, y, padLeft + plotW, y);

            String label = formatAxisTick(value, yMax);
            g.setColor(TEXT_SLATE);
            g.drawString(label, padLeft - fm.stringWidth(label) - 10, y + 4);
        }

        g.setColor(AXIS_LINE);
        g.setStroke(new BasicStroke(1.4f));
        g.drawLine(padLeft, padTop, padLeft, padTop + plotH);

        // Rotated Y-axis label
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g2.setColor(TEXT_NAVY);
        FontMetrics tfm = g2.getFontMetrics();
        int titleW = tfm.stringWidth(yTitle);
        int centerY = padTop + plotH / 2;
        g2.rotate(-Math.PI / 2.0, 22, centerY);
        g2.drawString(yTitle, 22 - titleW / 2, centerY + 4);
        g2.dispose();
    }

    private void drawHighlightCallout(
            Graphics2D g,
            int centerX,
            int plotLeft,
            int plotRight,
            int plotTop,
            int plotBottom,
            int targetY,
            String text,
            Color accent) {
        Stroke prev = g.getStroke();
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
        g.drawLine(centerX, plotTop, centerX, plotBottom);
        g.setStroke(prev);

        Font badgeFont = new Font(Font.SANS_SERIF, Font.BOLD, 11);
        g.setFont(badgeFont);
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(text) + 16;
        int bh = 22;
        int bx = Math.max(plotLeft + 2, Math.min(plotRight - bw - 2, centerX - bw / 2));
        int by = Math.max(plotTop + 4, Math.min(targetY - bh - 6, plotBottom - bh - 4));

        g.setColor(new Color(239, 246, 255));
        g.fillRoundRect(bx, by, bw, bh, 6, 6);
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(bx, by, bw, bh, 6, 6);
        g.setColor(TEXT_NAVY);
        g.drawString(text, bx + 8, by + 15);
    }

    private void drawCenteredTick(Graphics2D g, String label, int centerX, int y) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(TEXT_SLATE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, centerX - fm.stringWidth(label) / 2, y);
    }

    private void drawXAxisLabel(Graphics2D g, int padLeft, int plotW, int y, String title) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(TEXT_NAVY);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, padLeft + (plotW - fm.stringWidth(title)) / 2, y);
    }

    private double computeProbabilityAxisMax(double rawMax, double minStandardMax) {
        if (!Double.isFinite(rawMax) || rawMax <= 0.0) {
            return minStandardMax;
        }
        if (rawMax >= 0.08) {
            double effectiveMax = Math.max(minStandardMax, rawMax);
            return niceAxisMax(effectiveMax, 0.05);
        }
        return niceMagnitudeAxisMax(rawMax);
    }

    private double niceMagnitudeAxisMax(double rawMax) {
        if (!Double.isFinite(rawMax) || rawMax <= 0.0) {
            return 1.0;
        }
        double padded = rawMax * 1.15;
        double exp = Math.floor(Math.log10(padded));
        double pow10 = Math.pow(10.0, exp);
        double mantissa = padded / pow10;
        double step;
        if (mantissa <= 1.5) {
            step = 0.25 * pow10;
        } else if (mantissa <= 3.0) {
            step = 0.5 * pow10;
        } else if (mantissa <= 6.0) {
            step = 1.0 * pow10;
        } else {
            step = 2.0 * pow10;
        }
        return Math.ceil(padded / step) * step;
    }

    private double niceAxisMax(double rawMax, double stepUnit) {
        if (!Double.isFinite(rawMax) || rawMax <= 0.0) {
            return stepUnit;
        }
        double padded = rawMax * 1.12;
        return Math.ceil(padded / stepUnit) * stepUnit;
    }

    private String formatAxisTick(double val, double yMax) {
        if (val == 0.0) {
            return yMax < 1.5 ? "0.00" : "0";
        }
        if (yMax < 0.001) {
            return String.format(Locale.US, "%.2e", val);
        }
        if (yMax < 0.02) {
            return String.format(Locale.US, "%.4f", val);
        }
        if (yMax <= 0.15) {
            return String.format(Locale.US, "%.3f", val);
        }
        if (yMax <= 1.5) {
            return String.format(Locale.US, "%.2f", val);
        }
        if (yMax <= 5.0) {
            return String.format(Locale.US, "%.1f", val);
        }
        if (yMax < 10_000.0) {
            return String.format(Locale.US, "%.0f", val);
        }
        if (yMax < 1_000_000.0) {
            return formatAxisSuffix(val / 1_000.0, "K");
        }
        if (yMax < 1_000_000_000.0) {
            return formatAxisSuffix(val / 1_000_000.0, "M");
        }
        return String.format(Locale.US, "%.1e", val);
    }

    private String formatAxisSuffix(double scaled, String suffix) {
        if (Math.abs(scaled - Math.rint(scaled)) < 0.05) {
            return String.format(Locale.US, "%.0f%s", scaled, suffix);
        }
        return String.format(Locale.US, "%.1f%s", scaled, suffix);
    }

    private int computeLabelStep(int count, int maxVisible) {
        if (count <= maxVisible) {
            return 1;
        }
        return (int) Math.ceil((double) count / maxVisible);
    }

    private byte[] encodePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode report chart image.", ex);
        }
    }
}

