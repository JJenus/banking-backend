package com.jjenus.banking.reporting.application;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.jjenus.banking.reporting.domain.AccountStatement;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates a PDF account statement from an {@link AccountStatement} using
 * OpenPDF (LGPL 2.1 — commercial-safe, self-contained, no external calls).
 *
 * <p>OpenPDF package: {@code com.lowagie.text}
 */
@Component
public class StatementPdfGenerator {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneOffset.UTC);

    private static final Color BRAND_DARK  = new Color(13, 27, 42);    // #0d1b2a
    private static final Color BRAND_MID   = new Color(37, 99, 235);   // #2563eb
    private static final Color LIGHT_GREY  = new Color(243, 244, 246); // #f3f4f6
    private static final Color BORDER_GREY = new Color(209, 213, 219); // #d1d5db
    private static final Color GREEN       = new Color(21, 128, 61);
    private static final Color RED         = new Color(192, 57, 43);

    /**
     * Generates a PDF statement and returns it as a byte array.
     *
     * @param statement the assembled account statement
     * @return raw PDF bytes suitable for writing to an HTTP response
     */
    public byte[] generate(AccountStatement statement) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            addHeader(doc, statement);
            addSummaryBox(doc, statement);
            addTransactionTable(doc, statement);
            addFooter(doc, statement);

        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate statement PDF", e);
        } finally {
            doc.close();
        }

        return out.toByteArray();
    }

    // ── Sections ──────────────────────────────────────────────────────────

    private void addHeader(Document doc, AccountStatement statement) throws DocumentException {
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_DARK);
        Font subFont   = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);

        Paragraph title = new Paragraph("Account Statement", brandFont);
        title.setAlignment(Element.ALIGN_LEFT);
        doc.add(title);

        Paragraph sub = new Paragraph(
            "Generated: " + DATE_FMT.format(statement.generatedAt()) + " UTC", subFont);
        sub.setSpacingAfter(16);
        doc.add(sub);

        // Horizontal rule
        doc.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(
            0.5f, 100, BRAND_DARK, Element.ALIGN_CENTER, -2)));
        doc.add(Chunk.NEWLINE);
    }

    private void addSummaryBox(Document doc, AccountStatement statement) throws DocumentException {
        Font labelFont  = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
        Font valueFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_DARK);

        PdfPTable meta = new PdfPTable(4);
        meta.setWidthPercentage(100);
        meta.setSpacingBefore(12);
        meta.setSpacingAfter(20);
        meta.setWidths(new float[]{2f, 2f, 1.5f, 1.5f});

        addMetaCell(meta, "Account", statement.accountId(),         labelFont, valueFont);
        addMetaCell(meta, "Account Holder", statement.ownerName(),  labelFont, valueFont);
        addMetaCell(meta, "Period From", DATE_FMT.format(statement.periodFrom()), labelFont, valueFont);
        addMetaCell(meta, "Period To",   DATE_FMT.format(statement.periodTo()),   labelFont, valueFont);
        addMetaCell(meta, "Currency",    statement.currency(),      labelFont, valueFont);
        addMetaCell(meta, "Opening Balance", fmt(statement.openingBalance()), labelFont, valueFont);
        addMetaCell(meta, "Closing Balance", fmt(statement.closingBalance()), labelFont, valueFont);
        addMetaCell(meta, "Transactions", String.valueOf(statement.lines().size()), labelFont, valueFont);

        doc.add(meta);
    }

    private void addTransactionTable(Document doc, AccountStatement statement) throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont   = FontFactory.getFont(FontFactory.HELVETICA, 9, BRAND_DARK);
        Font monoFont   = FontFactory.getFont(FontFactory.COURIER, 8, Color.GRAY);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.2f, 1.2f, 3.5f, 1.5f, 1.5f, 1.5f});
        table.setSpacingBefore(4);

        // Header row
        String[] headers = {"Date", "Type", "Description / Ref", "Debit", "Credit", "Balance"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(BRAND_DARK);
            cell.setPadding(6);
            cell.setBorderColor(BRAND_DARK);
            table.addCell(cell);
        }

        // Data rows
        boolean alt = false;
        for (AccountStatement.StatementLine line : statement.lines()) {
            Color bg = alt ? LIGHT_GREY : Color.WHITE;
            alt = !alt;

            boolean isCredit = line.amount().compareTo(BigDecimal.ZERO) > 0;

            addDataCell(table, DATE_FMT.format(line.timestamp()),   cellFont, bg, Element.ALIGN_LEFT);
            addDataCell(table, line.type().replace("_", " "),       cellFont, bg, Element.ALIGN_CENTER);

            // Description + reference in one cell
            String descRef = line.description() != null ? line.description() : "";
            if (line.reference() != null && !line.reference().isBlank()) {
                descRef += "\n" + line.reference();
            }
            addDataCell(table, descRef, monoFont, bg, Element.ALIGN_LEFT);

            // Debit column (negative amounts)
            String debit  = isCredit ? "" : fmt(line.amount().abs());
            String credit = isCredit ? fmt(line.amount()) : "";
            Font debitFont  = FontFactory.getFont(FontFactory.HELVETICA, 9, RED);
            Font creditFont = FontFactory.getFont(FontFactory.HELVETICA, 9, GREEN);

            PdfPCell debitCell = new PdfPCell(new Phrase(debit, debitFont));
            debitCell.setBackgroundColor(bg);
            debitCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            debitCell.setPadding(5);
            table.addCell(debitCell);

            PdfPCell creditCell = new PdfPCell(new Phrase(credit, creditFont));
            creditCell.setBackgroundColor(bg);
            creditCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            creditCell.setPadding(5);
            table.addCell(creditCell);

            addDataCell(table, fmt(line.balanceAfter()), cellFont, bg, Element.ALIGN_RIGHT);
        }

        doc.add(table);
    }

    private void addFooter(Document doc, AccountStatement statement) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        doc.add(Chunk.NEWLINE);
        doc.add(new com.lowagie.text.pdf.draw.LineSeparator(
            0.5f, 100, BORDER_GREY, Element.ALIGN_CENTER, -2));
        Paragraph footer = new Paragraph(
            "\nThis is an official account statement. " +
            "Closing balance: " + statement.currency() + " " + fmt(statement.closingBalance()) +
            ". For queries contact your banking services provider.", footerFont);
        footer.setSpacingBefore(8);
        doc.add(footer);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void addMetaCell(PdfPTable table, String label, String value,
                              Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.addElement(new Phrase(label, labelFont));
        cell.addElement(new Phrase(value != null ? value : "—", valueFont));
        cell.setBorderColor(BORDER_GREY);
        cell.setPadding(8);
        table.addCell(cell);
    }

    private void addDataCell(PdfPTable table, String text, Font font,
                              Color bg, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorderColor(BORDER_GREY);
        table.addCell(cell);
    }

    private String fmt(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%,.2f", amount);
    }
}
