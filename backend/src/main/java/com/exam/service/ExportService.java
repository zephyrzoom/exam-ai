package com.exam.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.entity.Paper;
import com.exam.entity.PaperQuestion;
import com.exam.entity.Question;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    private static final String[] TYPE_ORDER = {Consts.Q_SINGLE, Consts.Q_MULTI, Consts.Q_JUDGE, Consts.Q_ESSAY};
    private static final String[] LETTERS = "ABCDEFGH".split("");

    public void exportPaper(Paper paper, List<PaperQuestion> pqs, String format, HttpServletResponse response) throws IOException {
        Map<String, List<PaperQuestion>> grouped = new LinkedHashMap<>();
        for (String type : TYPE_ORDER) {
            List<PaperQuestion> list = new ArrayList<>();
            for (PaperQuestion pq : pqs) {
                if (pq.getQuestion() != null && type.equals(pq.getQuestion().getType())) list.add(pq);
            }
            if (!list.isEmpty()) grouped.put(type, list);
        }
        String fileName = URLEncoder.encode(paper.getName(), "UTF-8");
        if ("pdf".equalsIgnoreCase(format)) {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".pdf");
            exportPdf(paper, grouped, response);
        } else {
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".docx");
            exportWord(paper, grouped, response);
        }
    }

    private void exportWord(Paper paper, Map<String, List<PaperQuestion>> grouped, HttpServletResponse response) throws IOException {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun tr = title.createRun();
        tr.setText(paper.getName());
        tr.setBold(true);
        tr.setFontSize(18);
        tr.setFontFamily("宋体");

        XWPFParagraph info = doc.createParagraph();
        info.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun ir = info.createRun();
        ir.setText("总分：" + paper.getTotalScore() + " 分    题量：" + paper.getQuestionCount()
                + " 题    状态：" + (Consts.PAPER_PUBLISHED.equals(paper.getStatus()) ? "已发布" : "草稿"));
        ir.setFontSize(10);

        int no = 0;
        int section = 0;
        String[] cnNum = {"一", "二", "三", "四", "五"};
        for (Map.Entry<String, List<PaperQuestion>> e : grouped.entrySet()) {
            XWPFParagraph sec = doc.createParagraph();
            XWPFRun sr = sec.createRun();
            sr.setText(cnNum[section++] + "、" + QuestionService.typeName(e.getKey()) + "题");
            sr.setBold(true);
            sr.setFontSize(13);
            for (PaperQuestion pq : e.getValue()) {
                no++;
                Question q = pq.getQuestion();
                XWPFParagraph qp = doc.createParagraph();
                XWPFRun qr = qp.createRun();
                qr.setText(no + ". （" + pq.getScore() + "分）" + q.getContent());
                qr.setFontSize(11);
                if (StrUtil.isNotBlank(q.getOptionsJson())) {
                    List<String> opts = JSONUtil.toList(q.getOptionsJson(), String.class);
                    for (int i = 0; i < opts.size(); i++) {
                        XWPFParagraph op = doc.createParagraph();
                        op.setIndentationLeft(400);
                        XWPFRun or = op.createRun();
                        or.setText(LETTERS[i] + ". " + opts.get(i));
                        or.setFontSize(11);
                    }
                }
                if (Consts.Q_ESSAY.equals(q.getType())) {
                    XWPFParagraph blank = doc.createParagraph();
                    blank.createRun().setText("答：________________________________________________");
                }
            }
        }

        doc.createParagraph().createRun().addBreak(BreakType.PAGE);
        XWPFParagraph ansTitle = doc.createParagraph();
        XWPFRun atr = ansTitle.createRun();
        atr.setText("参考答案与评分要点（线下审核用，注意保密）");
        atr.setBold(true);
        atr.setFontSize(14);
        no = 0;
        for (Map.Entry<String, List<PaperQuestion>> e : grouped.entrySet()) {
            for (PaperQuestion pq : e.getValue()) {
                no++;
                Question q = pq.getQuestion();
                XWPFParagraph ap = doc.createParagraph();
                XWPFRun ar = ap.createRun();
                StringBuilder sb = new StringBuilder(no + ". 【" + QuestionService.typeName(q.getType()) + "】答案：" + StrUtil.nullToEmpty(q.getAnswer()));
                ar.setText(sb.toString());
                ar.setFontSize(10);
                if (StrUtil.isNotBlank(q.getScorePoints())) {
                    XWPFParagraph sp = doc.createParagraph();
                    sp.createRun().setText("评分要点：" + q.getScorePoints());
                }
                if (StrUtil.isNotBlank(q.getAnalysis())) {
                    XWPFParagraph xp = doc.createParagraph();
                    xp.createRun().setText("解析：" + q.getAnalysis());
                }
            }
        }
        doc.write(response.getOutputStream());
        doc.close();
    }

    private void exportPdf(Paper paper, Map<String, List<PaperQuestion>> grouped, HttpServletResponse response) {
        try {
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();
            BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(bf, 18, Font.BOLD);
            Font secFont = new Font(bf, 13, Font.BOLD);
            Font bodyFont = new Font(bf, 11, Font.NORMAL);
            Font smallFont = new Font(bf, 10, Font.NORMAL);

            Paragraph title = new Paragraph(paper.getName(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph info = new Paragraph("总分：" + paper.getTotalScore() + " 分    题量：" + paper.getQuestionCount() + " 题", smallFont);
            info.setAlignment(Element.ALIGN_CENTER);
            info.setSpacingAfter(12);
            doc.add(info);

            int no = 0;
            int section = 0;
            String[] cnNum = {"一", "二", "三", "四", "五"};
            for (Map.Entry<String, List<PaperQuestion>> e : grouped.entrySet()) {
                Paragraph sec = new Paragraph(cnNum[section++] + "、" + QuestionService.typeName(e.getKey()) + "题", secFont);
                sec.setSpacingBefore(10);
                sec.setSpacingAfter(6);
                doc.add(sec);
                for (PaperQuestion pq : e.getValue()) {
                    no++;
                    Question q = pq.getQuestion();
                    doc.add(new Paragraph(no + ". （" + pq.getScore() + "分）" + q.getContent(), bodyFont));
                    if (StrUtil.isNotBlank(q.getOptionsJson())) {
                        List<String> opts = JSONUtil.toList(q.getOptionsJson(), String.class);
                        for (int i = 0; i < opts.size(); i++) {
                            Paragraph op = new Paragraph(LETTERS[i] + ". " + opts.get(i), bodyFont);
                            op.setIndentationLeft(20);
                            doc.add(op);
                        }
                    }
                    if (Consts.Q_ESSAY.equals(q.getType())) {
                        doc.add(new Paragraph("答：________________________________________________", bodyFont));
                    }
                }
            }

            doc.newPage();
            doc.add(new Paragraph("参考答案与评分要点（线下审核用，注意保密）", secFont));
            no = 0;
            for (Map.Entry<String, List<PaperQuestion>> e : grouped.entrySet()) {
                for (PaperQuestion pq : e.getValue()) {
                    no++;
                    Question q = pq.getQuestion();
                    doc.add(new Paragraph(no + ". 【" + QuestionService.typeName(q.getType()) + "】答案：" + StrUtil.nullToEmpty(q.getAnswer()), smallFont));
                    if (StrUtil.isNotBlank(q.getScorePoints())) {
                        doc.add(new Paragraph("评分要点：" + q.getScorePoints(), smallFont));
                    }
                    if (StrUtil.isNotBlank(q.getAnalysis())) {
                        doc.add(new Paragraph("解析：" + q.getAnalysis(), smallFont));
                    }
                }
            }
            doc.close();
        } catch (DocumentException e) {
            throw new BusinessException("PDF 导出失败：" + e.getMessage());
        } catch (IOException e) {
            throw new BusinessException("PDF 导出失败：" + e.getMessage());
        }
    }
}