package bo.utb.ajedrez.reporte;

import bo.utb.ajedrez.comun.NoEncontradoException;
import bo.utb.ajedrez.partida.Jugada;
import bo.utb.ajedrez.partida.Partida;
import bo.utb.ajedrez.partida.PartidaRepository;
import static bo.utb.ajedrez.partida.PartidaService.pgn;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color VERDE = new Color(46, 94, 66);
    private static final Font TITULO = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font NORMAL = new Font(Font.HELVETICA, 10);
    private static final Font CABECERA = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);

    private final PartidaRepository partidas;

    public ReporteService(PartidaRepository partidas) {
        this.partidas = partidas;
    }

    /** Listado de todas las partidas en PDF. */
    public byte[] partidasPdf(Long torneoId) {
        List<Partida> ps = partidas.buscar(null, torneoId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("Federación Boliviana de Ajedrez · Reporte de partidas", TITULO));
        doc.add(new Paragraph("Generado el " + FECHA.format(LocalDateTime.now()) + " · " + ps.size() + " partidas", NORMAL));
        doc.add(Chunk.NEWLINE);

        PdfPTable t = tabla(new float[]{1, 4, 4, 4, 1.5f, 2.5f, 1.5f, 3},
                "N°", "Blancas", "Negras", "Torneo", "Ronda", "Resultado", "Jugadas", "Fecha");
        for (Partida p : ps) {
            celdas(t, p.getId(), p.getBlancas(), p.getNegras(), torneo(p), texto(p.getRonda()),
                    resultado(p), p.getJugadas().size(), FECHA.format(p.getCreadaEn()));
        }
        doc.add(t);
        doc.close();
        log.info("Reporte PDF de partidas generado ({} partidas)", ps.size());
        return out.toByteArray();
    }

    /** Planilla de una partida: datos, jugadas numeradas y PGN. */
    public byte[] planillaPdf(Long id) {
        Partida p = partidas.findById(id).orElseThrow(() -> new NoEncontradoException("Partida " + id + " no existe"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("Planilla de partida N° " + p.getId(), TITULO));
        doc.add(new Paragraph(p.getBlancas() + " (blancas) – " + p.getNegras() + " (negras)", new Font(Font.HELVETICA, 12, Font.BOLD)));
        doc.add(new Paragraph(torneo(p) + (p.getRonda() != null ? " · Ronda " + p.getRonda() : "")
                + " · " + FECHA.format(p.getCreadaEn()) + " · Resultado: " + resultado(p), NORMAL));
        doc.add(Chunk.NEWLINE);

        PdfPTable t = tabla(new float[]{1, 3, 2, 3, 2}, "N°", "Blancas", "Origen", "Negras", "Origen");
        List<Jugada> js = p.getJugadas();
        for (int i = 0; i < js.size(); i += 2) {
            Jugada b = js.get(i);
            Jugada n = i + 1 < js.size() ? js.get(i + 1) : null;
            celdas(t, i / 2 + 1, b.getSan(), origen(b), n == null ? "" : n.getSan(), n == null ? "" : origen(n));
        }
        doc.add(t);
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("PGN", new Font(Font.HELVETICA, 11, Font.BOLD)));
        doc.add(new Paragraph(pgn(p), new Font(Font.COURIER, 10)));
        doc.close();
        log.info("Planilla PDF generada partida={}", id);
        return out.toByteArray();
    }

    /** Partidas en Excel: una hoja con el listado y el PGN de cada una. */
    public byte[] partidasExcel(Long torneoId) throws IOException {
        List<Partida> ps = partidas.buscar(null, torneoId);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = wb.createSheet("Partidas");
            CellStyle negrita = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            negrita.setFont(f);

            String[] cols = {"N°", "Blancas", "Negras", "Torneo", "Ronda", "Estado", "Resultado", "Jugadas",
                    "Leídas por cámara", "Fecha", "PGN"};
            Row cab = hoja.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = cab.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(negrita);
            }
            int r = 1;
            for (Partida p : ps) {
                Row row = hoja.createRow(r++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getBlancas());
                row.createCell(2).setCellValue(p.getNegras());
                row.createCell(3).setCellValue(torneo(p));
                if (p.getRonda() != null) row.createCell(4).setCellValue(p.getRonda());
                row.createCell(5).setCellValue(p.getEstado().name());
                row.createCell(6).setCellValue(p.getResultado());
                row.createCell(7).setCellValue(p.getJugadas().size());
                row.createCell(8).setCellValue(p.getJugadas().stream().filter(j -> j.getOrigen() == Jugada.Origen.VISION).count());
                row.createCell(9).setCellValue(FECHA.format(p.getCreadaEn()));
                row.createCell(10).setCellValue(pgn(p));
            }
            for (int i = 0; i < cols.length - 1; i++) hoja.autoSizeColumn(i);
            hoja.createFreezePane(0, 1);
            wb.write(out);
            log.info("Reporte Excel de partidas generado ({} partidas)", ps.size());
            return out.toByteArray();
        }
    }

    private static PdfPTable tabla(float[] anchos, String... titulos) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String s : titulos) {
            PdfPCell c = new PdfPCell(new Phrase(s, CABECERA));
            c.setBackgroundColor(VERDE);
            c.setPadding(5);
            t.addCell(c);
        }
        return t;
    }

    private static void celdas(PdfPTable t, Object... valores) {
        for (Object v : valores) {
            PdfPCell c = new PdfPCell(new Phrase(String.valueOf(v), NORMAL));
            c.setPadding(4);
            t.addCell(c);
        }
    }

    private static String texto(Object o) {
        return o == null ? "—" : o.toString();
    }

    private static String torneo(Partida p) {
        String c = p.getTorneo().getCategoria();
        return p.getTorneo().getNombre() + (c == null ? "" : " (" + c + ")");
    }

    private static String resultado(Partida p) {
        return p.getEstado() == Partida.Estado.EN_CURSO ? "En curso" : p.getResultado();
    }

    private static String origen(Jugada j) {
        if (j.getOrigen() == Jugada.Origen.MANUAL) return "Manual";
        return j.getConfianza() == null ? "Cámara" : "Cámara " + Math.round(j.getConfianza() * 100) + "%";
    }
}
