import express from "express";
import { createServer as createViteServer } from "vite";
import path from "path";
import { fileURLToPath } from "url";
import { Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, Table, TableRow, TableCell, WidthType } from "docx";
import { GoogleGenAI } from "@google/genai";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SYSTEM_PROMPT = `You are a high-fidelity document reconstruction agent. Prioritize exact source fidelity over interpretation or rewriting.

STRICT CONTENT RULES:
- Transcribe the original wording exactly; never paraphrase, summarize, correct spelling, normalize Arabic letters, or invent missing text.
- Preserve Arabic characters, hamza forms, alif/yaa variants, diacritics (tashkeel), punctuation, numerals, and right-to-left reading order as faithfully as visible.
- Preserve paragraph order, line breaks where meaningful, table row/column order, and blank cells. Represent tables as tables, not prose.
- If a character or value cannot be read confidently, mark it as [غير واضح] rather than guessing.
- Do not claim exact formatting recovery when the PDF does not expose it. Infer formatting only when visually supported.

Output JSON Format:
{
  "structure": Array<Paragraph | Table>,
  "insights": {
    "primaryLanguage": string,
    "secondaryLanguages": string[],
    "summary": string,
    "tone": string,
    "complexity": string
  }
}

Paragraph/Table schema must follow precisely to ensure Word compatibility.
1. FONT PRESERVATION: Identify EXACT font families.
2. ARABIC/RTL: Set "rtl": true and "alignment": "right". Detect specific fonts.
3. Return ONLY the raw JSON object.`;

async function startServer() {
  const app = express();
  const PORT = 3000;

  const genAI = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY || "" });

  app.use(express.json({ limit: '100mb' }));

  // Log all requests for debugging
  app.use((req, res, next) => {
    if (req.path.startsWith('/api')) {
      console.log(`[API] ${req.method} ${req.path}`);
    }
    next();
  });

  // Helper to create a paragraph from the new schema
  const createParagraph = (p: any) => {
    const isRtl = !!p.rtl;
    
    return new Paragraph({
      heading: p.level === 1 ? HeadingLevel.HEADING_1 : p.level === 2 ? HeadingLevel.HEADING_2 : undefined,
      alignment: p.alignment === 'center' ? AlignmentType.CENTER : 
                 (p.alignment === 'right' || isRtl) ? AlignmentType.RIGHT : 
                 p.alignment === 'both' ? AlignmentType.JUSTIFIED : AlignmentType.LEFT,
      bidirectional: isRtl,
      spacing: p.spacing ? {
        before: p.spacing.before ? p.spacing.before * 20 : undefined,
        after: p.spacing.after ? p.spacing.after * 20 : undefined,
        line: p.spacing.line ? p.spacing.line * 240 : undefined,
      } : undefined,
      indent: p.indent ? {
        left: p.indent.left ? p.indent.left * 20 : undefined,
        firstLine: p.indent.firstLine ? p.indent.firstLine * 20 : undefined,
      } : undefined,
      children: (p.runs || []).map((run: any) => {
        const detectedFont = run.fontFamily?.trim();
        const fallbackFont = isRtl ? "Traditional Arabic" : "Calibri";
        const chosenFont = detectedFont || fallbackFont;

        if (detectedFont) {
          console.log(`[DOCX] Applying detected font: "${detectedFont}" (Paragraph RTL: ${isRtl})`);
        }

        return new TextRun({
          text: run.text || "",
          bold: !!run.bold,
          italics: !!run.italic,
          underline: run.underline ? {} : undefined,
          size: run.fontSize ? run.fontSize * 2 : 24,
          font: {
            ascii: chosenFont,
            cs: chosenFont,
            hAnsi: chosenFont,
            eastAsia: chosenFont,
          },
          color: run.color ? run.color.replace('#', '') : undefined,
          rightToLeft: isRtl,
          sizeComplexScript: run.fontSize ? run.fontSize * 2 : 24,
          boldComplexScript: !!run.bold,
          italicsComplexScript: !!run.italic,
        });
      }),
    });
  };

  // API to process PDF via Gemini
  app.post("/api/process-pdf", async (req, res) => {
    try {
      const { base64 } = req.body;
      if (!base64) return res.status(400).json({ error: "Missing base64 data" });

      const response = await genAI.models.generateContent({
        model: "gemini-2.0-flash",
        contents: [
          {
            role: "user",
            parts: [
              { text: SYSTEM_PROMPT },
              { text: "Convert this PDF to its structured representation for Word document generation." },
              { inlineData: { data: base64, mimeType: "application/pdf" } }
            ]
          }
        ],
        config: {
          responseMimeType: "application/json",
        }
      });

      res.json({ result: response.text });
    } catch (error) {
      console.error("Gemini Error:", error);
      res.status(500).json({ error: "AI processing failed" });
    }
  });

  // API to generate DOCX from structured JSON
  app.post("/api/generate-docx", async (req, res) => {
    try {
      const { filename, content } = req.body;
      
      if (!content || !Array.isArray(content)) {
        return res.status(400).json({ error: "Invalid content format" });
      }

      const doc = new Document({
        sections: [
          {
            properties: {},
            children: content.map((item: any) => {
              if (item.type === 'table') {
                return new Table({
                  width: { size: 100, type: WidthType.PERCENTAGE },
                  rows: (item.rows || []).map((row: any) => new TableRow({
                    children: (row.cells || []).map((cell: any) => new TableCell({
                      width: cell.width ? { size: cell.width, type: WidthType.PERCENTAGE } : undefined,
                      children: (cell.paragraphs || []).map((p: any) => createParagraph(p))
                    }))
                  }))
                });
              }
              return createParagraph(item);
            }),
          },
        ],
      });

      const buffer = await Packer.toBuffer(doc);
      
      res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      res.setHeader('Content-Disposition', `attachment; filename="${filename || 'converted'}.docx"`);
      res.send(buffer);
    } catch (error) {
      console.error("DOCX Generation Error:", error);
      res.status(500).json({ error: "Failed to generate Word document" });
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
