/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { 
  FileText, 
  Upload, 
  X, 
  CheckCircle2, 
  Loader2, 
  Download, 
  Files,
  Languages,
  ArrowRight,
  ShieldCheck,
  Zap,
  BrainCircuit,
  MessageSquareQuote,
  Target,
  Sparkles,
  SearchCheck
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import confetti from 'canvas-confetti';
import { Document, Packer, Paragraph, Table, TableRow, TableCell } from 'docx';
import * as pdfjsLib from 'pdfjs-dist';
import * as XLSX from 'xlsx';
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

// PDF.js runs locally in the browser/WebView; Vite bundles the worker with the app.
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl;

interface SaveFilePlugin {
  saveFile(options: { filename: string; mimeType: string; data: string }): Promise<{ uri: string }>;
}
interface OpenPdfPlugin {
  openPdf(options: { filename: string; data: string }): Promise<{ opened: boolean }>;
}
interface OpenDocumentPlugin {
  openDocument(options: { filename: string; mimeType: string; data: string }): Promise<{ opened: boolean }>;
}

const NativeSaveFile = registerPlugin<SaveFilePlugin>('SaveFile');
const NativeOpenPdf = registerPlugin<OpenPdfPlugin>('OpenPdf');
const NativeOpenDocument = registerPlugin<OpenDocumentPlugin>('OpenDocument');

// --- Constants & Types ---

interface ConversionFile {
  id: string;
  file: File;
  status: 'idle' | 'processing' | 'completed' | 'error';
  progress: number;
  resultUrl?: string;
  previewUrl?: string;
  error?: string;
  insights?: {
    primaryLanguage: string;
    secondaryLanguages: string[];
    summary: string;
    tone: string;
    complexity: string;
  };
}

// --- Components ---

// Local PDF.js canvas viewer with page navigation and zoom.
function PdfCanvasViewer({ file }: { file: File }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [pageCount, setPageCount] = useState(0);
  const [scale, setScale] = useState(1.15);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { setPageNumber(1); setPageCount(0); setError(''); }, [file]);
  useEffect(() => {
    let cancelled = false;
    let loadingTask: ReturnType<typeof pdfjsLib.getDocument> | undefined;
    let pdfDocument: Awaited<ReturnType<typeof pdfjsLib.getDocument>['promise']> | undefined;
    let renderTask: { cancel: () => void } | undefined;
    const renderPage = async () => {
      setBusy(true); setError('');
      try {
        const data = new Uint8Array(await file.arrayBuffer());
        if (cancelled) return;
        loadingTask = pdfjsLib.getDocument({ data, isEvalSupported: false, useSystemFonts: true });
        pdfDocument = await loadingTask.promise;
        if (cancelled) { await pdfDocument.destroy(); return; }
        setPageCount(pdfDocument.numPages);
        const safePage = Math.min(Math.max(1, pageNumber), pdfDocument.numPages);
        if (safePage !== pageNumber) setPageNumber(safePage);
        const page = await pdfDocument.getPage(safePage);
        const viewport = page.getViewport({ scale });
        const canvas = canvasRef.current;
        const context = canvas?.getContext('2d', { alpha: false });
        if (!canvas || !context) throw new Error('تعذر تجهيز مساحة عرض صفحة PDF.');
        canvas.width = Math.ceil(viewport.width); canvas.height = Math.ceil(viewport.height);
        renderTask = page.render({ canvasContext: context, viewport } as Parameters<typeof page.render>[0]);
        await (renderTask as unknown as { promise: Promise<unknown> }).promise;
        page.cleanup();
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'تعذر عرض صفحة PDF. قد يكون الملف محميًا أو غير صالح.');
      } finally { if (!cancelled) setBusy(false); }
    };
    void renderPage();
    return () => { cancelled = true; try { renderTask?.cancel(); } catch { /* render may already be finished */ } void pdfDocument?.destroy().catch(() => undefined); void loadingTask?.destroy().catch(() => undefined); };
  }, [file, pageNumber, scale]);

  return <div className="flex min-h-0 flex-1 flex-col gap-3">
    <div className="flex flex-wrap items-center justify-center gap-2 text-sm text-white">
      <button disabled={pageNumber <= 1 || busy} onClick={() => setPageNumber(p => Math.max(1, p - 1))} className="rounded-lg bg-white/10 px-3 py-2 disabled:opacity-40">السابق</button>
      <span dir="rtl">صفحة {pageCount ? Math.min(pageNumber, pageCount) : pageNumber} من {pageCount || '…'}</span>
      <button disabled={pageCount === 0 || pageNumber >= pageCount || busy} onClick={() => setPageNumber(p => Math.min(pageCount, p + 1))} className="rounded-lg bg-white/10 px-3 py-2 disabled:opacity-40">التالي</button>
      <button disabled={scale <= 0.6} onClick={() => setScale(v => Math.max(0.6, Number((v - 0.15).toFixed(2))))} className="rounded-lg bg-white/10 px-3 py-2">−</button>
      <span>{Math.round(scale * 100)}%</span>
      <button disabled={scale >= 2.4} onClick={() => setScale(v => Math.min(2.4, Number((v + 0.15).toFixed(2))))} className="rounded-lg bg-white/10 px-3 py-2">+</button>
    </div>
    {busy && <p className="text-center text-xs text-gray-300">جارٍ عرض الصفحة محليًا…</p>}
    {error && <p role="alert" className="rounded-lg bg-red-950 p-3 text-sm text-red-200">{error} — يمكنك استخدام خيار فتح الملف في عارض PDF آخر.</p>}
    <div className="min-h-0 flex-1 overflow-auto rounded-xl bg-gray-800 p-3 text-center">
      <canvas ref={canvasRef} aria-label={`صفحة PDF رقم ${pageNumber}`} className="mx-auto h-auto max-w-full shadow-xl" />
    </div>
    <p className="text-center text-xs text-gray-400">عرض محلي للصفحات. الملفات الكبيرة قد تستهلك ذاكرة الهاتف؛ هذا العارض لا يغيّر الملف الأصلي.</p>
  </div>;
}

export default function App() {
  const [files, setFiles] = useState<ConversionFile[]>([]);
  const [previewFile, setPreviewFile] = useState<ConversionFile | null>(null);
  const [localPreview, setLocalPreview] = useState<{
    name: string;
    kind: 'text' | 'csv' | 'docx' | 'xlsx' | 'access';
    accessInfo?: { format: string; size: number; signature: string; recognized: boolean };
    text: string;
    rows: string[][];
    blocks?: Array<{ type: 'paragraph' | 'table'; text?: string; rows?: string[][] }>;
    originalWorkbook?: ArrayBuffer;
    originalSheetName?: string;
    originalRows?: string[][];
  } | null>(null);
  const [notice, setNotice] = useState<string>('');
  // Privacy guard: blocks conversion requests that upload PDF data to the server.
  const [localOnlyMode, setLocalOnlyMode] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const updatePreviewCell = (rowIndex: number, columnIndex: number, value: string) => {
    setLocalPreview(prev => {
      if (!prev || (prev.kind !== 'xlsx' && prev.kind !== 'csv')) return prev;
      const rows = prev.rows.map(row => [...row]);
      while (rows[rowIndex].length <= columnIndex) rows[rowIndex].push('');
      rows[rowIndex][columnIndex] = value;
      return { ...prev, rows };
    });
  };

  const updateDocxParagraph = (blockIndex: number, value: string) => {
    setLocalPreview(prev => {
      if (!prev || prev.kind !== 'docx' || !prev.blocks) return prev;
      const blocks = prev.blocks.map((block, index) => index === blockIndex ? { ...block, text: value } : block);
      return { ...prev, blocks };
    });
  };

  const updateDocxCell = (blockIndex: number, rowIndex: number, columnIndex: number, value: string) => {
    setLocalPreview(prev => {
      if (!prev || prev.kind !== 'docx' || !prev.blocks) return prev;
      const blocks = prev.blocks.map((block, index) => {
        if (index !== blockIndex || block.type !== 'table') return block;
        const rows = (block.rows || []).map(row => [...row]);
        if (!rows[rowIndex]) rows[rowIndex] = [];
        rows[rowIndex][columnIndex] = value;
        return { ...block, rows };
      });
      return { ...prev, blocks };
    });
  };

  const exportEditedDocx = async () => {
    if (!localPreview || localPreview.kind !== 'docx') return;
    try {
      const children = (localPreview.blocks || []).map(block => {
        if (block.type === 'table') {
          return new Table({
            rows: (block.rows || []).map(row => new TableRow({
              children: row.map(cell => new TableCell({ children: [new Paragraph({ text: cell || '' })] })),
            })),
            width: { size: 100, type: 'pct' },
          });
        }
        return new Paragraph({ text: block.text || '' });
      });
      const blob = await Packer.toBlob(new Document({ sections: [{ children }] }));
      const filename = localPreview.name.replace(/\.docx$/i, '') + '-edited.docx';
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت تجهيز نسخة Word الجديدة.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({
          filename,
          mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          data,
        });
        setNotice(`تم حفظ ${filename} كملف جديد. الملف الأصلي لم يتغير.`);
      } else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. تم إنشاء مستند جديد من النصوص والجداول المعروضة، والأصل لم يتغير.`);
      }
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'تعذر تصدير نسخة Word الجديدة.');
    }
  };

  const xmlEscape = (value: string) => value
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&apos;');

  const columnLetters = (index: number) => {
    let n = index + 1;
    let out = '';
    while (n > 0) { const rem = (n - 1) % 26; out = String.fromCharCode(65 + rem) + out; n = Math.floor((n - 1) / 26); }
    return out;
  };

  // Creates a standards-based, minimal XLSX workbook using uncompressed ZIP entries.
  // This exports the edited visible values into a NEW workbook; it does not overwrite the original.
  const createXlsxBlob = (rows: string[][]) => {
    const sheetRows = rows.map((row, ri) => {
      const cells = row.map((value, ci) => {
        if (value === undefined || value === '') return '';
        const ref = `${columnLetters(ci)}${ri + 1}`;
        return `<c r="${ref}" t="inlineStr"><is><t xml:space="preserve">${xmlEscape(value)}</t></is></c>`;
      }).join('');
      return `<row r="${ri + 1}">${cells}</row>`;
    }).join('');
    const files: Array<{ name: string; data: string }> = [
      { name: '[Content_Types].xml', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>' },
      { name: '_rels/.rels', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>' },
      { name: 'xl/workbook.xml', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>' },
      { name: 'xl/_rels/workbook.xml.rels', data: '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>' },
      { name: 'xl/worksheets/sheet1.xml', data: `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${sheetRows}</sheetData></worksheet>` }
    ];
    const encoder = new TextEncoder();
    const crcTable = new Uint32Array(256);
    for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1); crcTable[n] = c >>> 0; }
    const crc32 = (bytes: Uint8Array) => { let crc = 0xFFFFFFFF; for (const b of bytes) crc = crcTable[(crc ^ b) & 0xFF] ^ (crc >>> 8); return (crc ^ 0xFFFFFFFF) >>> 0; };
    const localParts: Uint8Array[] = [];
    const centralParts: Uint8Array[] = [];
    let offset = 0;
    for (const file of files) {
      const name = encoder.encode(file.name);
      const data = encoder.encode(file.data);
      const crc = crc32(data);
      const local = new Uint8Array(30 + name.length + data.length);
      const lv = new DataView(local.buffer);
      lv.setUint32(0, 0x04034b50, true); lv.setUint16(4, 20, true); lv.setUint16(6, 0x0800, true);
      lv.setUint16(8, 0, true); lv.setUint32(14, crc, true); lv.setUint32(18, data.length, true); lv.setUint32(22, data.length, true);
      lv.setUint16(26, name.length, true); lv.setUint16(28, 0, true);
      local.set(name, 30); local.set(data, 30 + name.length); localParts.push(local);
      const central = new Uint8Array(46 + name.length);
      const cv = new DataView(central.buffer);
      cv.setUint32(0, 0x02014b50, true); cv.setUint16(4, 20, true); cv.setUint16(6, 20, true);
      cv.setUint16(8, 0x0800, true); cv.setUint16(10, 0, true); cv.setUint32(16, crc, true);
      cv.setUint32(20, data.length, true); cv.setUint32(24, data.length, true); cv.setUint16(28, name.length, true);
      cv.setUint32(42, offset, true); central.set(name, 46); centralParts.push(central);
      offset += local.length;
    }
    const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0);
    const end = new Uint8Array(22);
    const ev = new DataView(end.buffer);
    ev.setUint32(0, 0x06054b50, true); ev.setUint16(8, files.length, true); ev.setUint16(10, files.length, true);
    ev.setUint32(12, centralSize, true); ev.setUint32(16, offset, true);
    return new Blob([...localParts, ...centralParts, end], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  };

  const exportEditedXlsx = async () => {
    if (!localPreview || localPreview.kind !== 'xlsx') return;
    try {
      // Re-open the original workbook and apply only cells changed in the editor.
      // This keeps other sheets and unchanged formulas/data instead of rebuilding a one-sheet file.
      let blob: Blob;
      if (localPreview.originalWorkbook && localPreview.originalSheetName) {
        const workbook = XLSX.read(localPreview.originalWorkbook, { type: 'array', cellDates: false });
        const sheet = workbook.Sheets[localPreview.originalSheetName];
        if (!sheet) throw new Error('تعذر العثور على ورقة العمل الأصلية عند الحفظ.');
        const originalRows = localPreview.originalRows || [];
        for (let ri = 0; ri < localPreview.rows.length; ri++) {
          const editedRow = localPreview.rows[ri] || [];
          const originalRow = originalRows[ri] || [];
          for (let ci = 0; ci < editedRow.length; ci++) {
            const value = editedRow[ci] ?? '';
            if (value === (originalRow[ci] ?? '')) continue;
            const address = XLSX.utils.encode_cell({ r: ri, c: ci });
            const existing = sheet[address] as XLSX.CellObject | undefined;
            const nextCell: XLSX.CellObject = { ...(existing || {}), t: 's', v: value };
            delete nextCell.f;
            sheet[address] = nextCell;
          }
        }
        const output = XLSX.write(workbook, { bookType: 'xlsx', type: 'array', cellStyles: true });
        blob = new Blob([output], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      } else {
        // Compatibility fallback for previews created without source workbook metadata.
        blob = createXlsxBlob(localPreview.rows);
      }
      const filename = localPreview.name.replace(/\.xlsx$/i, '') + '-edited.xlsx';
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت تجهيز نسخة Excel الجديدة.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({
          filename,
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          data,
        });
        setNotice(`تم حفظ ${filename} كملف جديد. الملف الأصلي لم يتغير.`);
      } else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. تم إنشاء مصنف جديد من القيم المعروضة، والأصل لم يتغير.`);
      }
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'تعذر تصدير نسخة Excel الجديدة.');
    }
  };


  const exportCsvToXlsx = async () => {
    if (!localPreview || localPreview.kind !== 'csv') return;
    try {
      const blob = createXlsxBlob(localPreview.rows);
      const filename = localPreview.name.replace(/\.csv$/i, '') + '.xlsx';
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذّر تجهيز ملف Excel.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({
          filename,
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          data,
        });
        setNotice(`تم حفظ ${filename}. جرى تصدير جميع صفوف CSV إلى ورقة Excel واحدة.`);
      } else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. جرى تصدير جميع صفوف CSV إلى ورقة Excel واحدة.`);
      }
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'تعذّر تحويل CSV إلى Excel.');
    }
  };

  const onFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const selectedFiles = Array.from(e.target.files) as File[];
      const supported = selectedFiles.filter(f =>
        /\.(pdf|doc|docx|xls|xlsx|csv|txt|ppt|pptx|mdb|accdb)$/i.test(f.name)
      );
      const rejected = selectedFiles.length - supported.length;
      const newFiles: ConversionFile[] = supported.map(f => ({
        id: Math.random().toString(36).slice(2, 11),
        file: f,
        status: 'idle',
        progress: 0,
        previewUrl: /\.pdf$/i.test(f.name) ? URL.createObjectURL(f) : undefined,
      }));
      setFiles(prev => [...prev, ...newFiles]);
      setNotice(
        rejected > 0
          ? `تمت إضافة ${newFiles.length} ملفًا. تم تجاهل ${rejected} ملف غير مدعوم.`
          : `تمت إضافة ${newFiles.length} ملفًا. يمكنك معاينة PDF وDOCX وXLSX وCSV والنصوص محليًا، أو تحويل PDF عبر الخادم.`
      );
    }
  }, [files]);

  const saveConvertedFile = async (item: ConversionFile) => {
    if (!item.resultUrl) return;
    const filename = item.file.name.replace(/\.pdf$/i, '') + '.docx';
    try {
      const response = await fetch(item.resultUrl);
      const blob = await response.blob();
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت قراءة الملف الناتج'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({
          filename,
          mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          data,
        });
        setNotice(`تم حفظ ${filename} في الموقع الذي اخترته من نافذة حفظ الملفات.`);
      } else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. تحقق من مجلد التنزيلات.`);
      }
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'تعذر حفظ الملف. حاول مرة أخرى.');
    }
  };

  // Standards-based local PDF text extraction via PDF.js (not OCR).
  const extractPdfTextLocally = async (item: ConversionFile) => {
    let loadingTask: ReturnType<typeof pdfjsLib.getDocument> | undefined;
    try {
      if (item.file.size > 100 * 1024 * 1024) {
        setNotice('الملف أكبر من 100 ميغابايت. أوقفنا الاستخراج لتقليل استهلاك ذاكرة الهاتف.');
        return;
      }
      const bytes = new Uint8Array(await item.file.arrayBuffer());
      if (new TextDecoder('latin1').decode(bytes.subarray(0, 5)) !== '%PDF-') {
        throw new Error('الملف لا يحمل ترويسة PDF متوقعة.');
      }
      loadingTask = pdfjsLib.getDocument({
        data: bytes,
        isEvalSupported: false,
        useSystemFonts: true,
        stopAtErrors: false,
      });
      loadingTask.onPassword = (updatePassword, reason) => {
        const promptText = reason === pdfjsLib.PasswordResponses.NEED_PASSWORD
          ? 'هذا الملف محمي بكلمة مرور. أدخل كلمة المرور لقراءته محليًا:'
          : 'كلمة المرور غير صحيحة. حاول مرة أخرى:';
        const password = window.prompt(promptText);
        if (password === null) {
          setNotice('أُلغي فتح ملف PDF المحمي. لم يتم إرسال الملف إلى أي خادم.');
          void loadingTask?.destroy();
          return;
        }
        updatePassword(password);
      };
      const pdf = await loadingTask.promise;
      const maxPages = Math.min(pdf.numPages, 200);
      const output: string[] = [];
      for (let pageNumber = 1; pageNumber <= maxPages; pageNumber++) {
        const page = await pdf.getPage(pageNumber);
        const content = await page.getTextContent();
        const items = content.items.filter((entry): entry is typeof entry & { str: string; transform: number[]; hasEOL?: boolean } =>
          'str' in entry && typeof entry.str === 'string' && 'transform' in entry
        );
        const lines: string[] = [];
        let line = '';
        let previousY: number | undefined;
        let previousXEnd: number | undefined;
        for (const textItem of items) {
          const value = textItem.str;
          const x = Number(textItem.transform[4] || 0);
          const y = Number(textItem.transform[5] || 0);
          const height = Math.max(1, Math.abs(Number(textItem.transform[3] || 10)));
          if (previousY !== undefined && Math.abs(y - previousY) > Math.max(2.5, height * 0.45)) {
            if (line.trim()) lines.push(line.trimEnd());
            line = '';
            previousXEnd = undefined;
          }
          if (value) {
            if (line && previousXEnd !== undefined && x - previousXEnd > Math.max(2, height * 0.22) && !/\s$/.test(line) && !/^\s/.test(value)) line += ' ';
            line += value;
          }
          previousY = y;
          const width = 'width' in textItem ? Number(textItem.width || 0) : 0;
          previousXEnd = x + width;
          if (textItem.hasEOL) {
            if (line.trim()) lines.push(line.trimEnd());
            line = '';
            previousXEnd = undefined;
            previousY = undefined;
          }
        }
        if (line.trim()) lines.push(line.trimEnd());
        output.push(`--- الصفحة ${pageNumber} ---\n${lines.join('\n')}`);
        page.cleanup();
      }
      const extracted = output.join('\n\n');
      if (!extracted.replace(/--- الصفحة \d+ ---/g, '').trim()) {
        setNotice(`لم يعثر PDF.js على نص قابل للاستخراج في أول ${maxPages} صفحة. قد يكون الملف ممسوحًا ضوئيًا ويحتاج إلى OCR.`);
        await pdf.destroy();
        return;
      }
      setLocalPreview({ name: item.file.name.replace(/\.pdf$/i, '') + '-extracted.pdf.txt', kind: 'text', text: extracted, rows: [] });
      const pageNote = pdf.numPages > maxPages ? ` تمت قراءة أول ${maxPages} صفحة من أصل ${pdf.numPages}.` : ` عدد الصفحات: ${pdf.numPages}.`;
      setNotice(`اكتمل استخراج النص محليًا باستخدام PDF.js دون رفع الملف.${pageNote} قد تختلف قراءة ترتيب النص العربي في بعض الملفات، ولا يتضمن هذا الاستخراج OCR للصفحات المصوّرة. راجع النص قبل الاعتماد عليه.`);
      await pdf.destroy();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      if (/password|encrypted|PasswordException/i.test(message)) {
        setNotice('تعذر فتح ملف PDF المحمي. تأكد من كلمة المرور أو من توافق التشفير مع PDF.js.');
      } else {
        setNotice(`تعذر استخراج النص من PDF محليًا: ${message}`);
      }
      try { await loadingTask?.destroy(); } catch { /* cleanup best-effort */ }
    }
  };

  // Experimental OCR for scanned PDFs. Tesseract's Arabic/English traineddata may be
  // downloaded on first use unless it is bundled by the build; OCR runs on-device.
  const runLocalPdfOcr = async (item: ConversionFile) => {
    let loadingTask: ReturnType<typeof pdfjsLib.getDocument> | undefined;
    let worker: { recognize: (image: HTMLCanvasElement) => Promise<{ data: { text: string } }>; terminate: () => Promise<unknown> } | undefined;
    try {
      if (item.file.size > 40 * 1024 * 1024) {
        setNotice('ملف PDF أكبر من 40 ميغابايت. اختر نسخة أصغر لتقليل استهلاك ذاكرة الهاتف أثناء OCR.');
        return;
      }
      setNotice('جارٍ تهيئة OCR العربي والإنجليزي من بيانات اللغة المحلية. يجب تجهيز public/tessdata قبل البناء للعمل دون إنترنت.');
      const tesseract = await import('tesseract.js');
      const bytes = new Uint8Array(await item.file.arrayBuffer());
      loadingTask = pdfjsLib.getDocument({ data: bytes, isEvalSupported: false, useSystemFonts: true });
      loadingTask.onPassword = (updatePassword, reason) => {
        const password = window.prompt(reason === pdfjsLib.PasswordResponses.NEED_PASSWORD
          ? 'أدخل كلمة مرور ملف PDF لمعالجته محليًا:' : 'كلمة المرور غير صحيحة. حاول مرة أخرى:');
        if (password === null) { setNotice('أُلغي OCR. لم يتم إرسال ملف PDF إلى خادم التحويل.'); void loadingTask?.destroy(); return; }
        updatePassword(password);
      };
      const pdf = await loadingTask.promise;
      const maxPages = Math.min(pdf.numPages, 5);
      worker = await tesseract.createWorker('ara+eng', 1, {
        langPath: `${import.meta.env.BASE_URL}tessdata`,
        cacheMethod: 'none',
        logger: (message: { status?: string; progress?: number }) => {
          if (message.status) setNotice(`OCR محلي: ${message.status}${typeof message.progress === 'number' ? ` (${Math.round(message.progress * 100)}%)` : ''}`);
        },
      }) as typeof worker;
      const output: string[] = [];
      for (let pageNumber = 1; pageNumber <= maxPages; pageNumber++) {
        setNotice(`OCR محلي للصفحة ${pageNumber} من ${maxPages}…`);
        const page = await pdf.getPage(pageNumber);
        const viewport = page.getViewport({ scale: 1.35 });
        const canvas = document.createElement('canvas');
        const context = canvas.getContext('2d', { willReadFrequently: true });
        if (!context) throw new Error('تعذر إنشاء مساحة الرسم اللازمة لـ OCR.');
        canvas.width = Math.ceil(viewport.width);
        canvas.height = Math.ceil(viewport.height);
        await page.render({ canvasContext: context, viewport, canvas } as Parameters<typeof page.render>[0]).promise;
        const result = await worker.recognize(canvas);
        output.push(`--- الصفحة ${pageNumber} ---\n${(result.data.text || '').trim()}`);
        canvas.width = 1; canvas.height = 1;
        page.cleanup();
      }
      const text = output.join('\n\n');
      if (!text.replace(/--- الصفحة \d+ ---/g, '').trim()) {
        setNotice('لم يتمكن OCR من التعرف على نص واضح. جرّب ملفًا أوضح أو صورة بدقة أعلى.');
      } else {
        setLocalPreview({ name: item.file.name.replace(/\.pdf$/i, '') + '-ocr.pdf.txt', kind: 'text', text, rows: [] });
        setNotice(`اكتمل OCR محليًا لأول ${maxPages} صفحات باستخدام العربية والإنجليزية. ${pdf.numPages > maxPages ? `عدد الصفحات ${pdf.numPages}؛ لم تُعالج الصفحات المتبقية.` : ''} راجع الحروف والتشكيل يدويًا؛ لا يمكن ضمان الدقة الكاملة.`);
      }
      await worker.terminate(); worker = undefined;
      await pdf.destroy();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setNotice(`تعذر تشغيل OCR: ${message}. تأكد من تثبيت تبعيات المشروع وتوفر بيانات اللغة العربية. لم يُرفع الملف إلى خادم التحويل بواسطة هذه الميزة.`);
      try { await worker?.terminate(); } catch { /* cleanup best-effort */ }
      try { await loadingTask?.destroy(); } catch { /* cleanup best-effort */ }
    }
  };

  const exportExtractedPdfTextToDocx = async () => {
    if (!localPreview || localPreview.kind !== 'text' || !/\.pdf\.txt$/i.test(localPreview.name)) return;
    try {
      // This creates an editable Word document from extracted text; it cannot restore
      // the original PDF layout, fonts, tables, or scanned content that OCR missed.
      // Preserve extracted page boundaries as labeled sections in Word. This is still
      // a text reconstruction: it does not recover PDF coordinates, images, or tables.
      const paragraphs: Paragraph[] = [];
      for (const line of localPreview.text.split(/\r?\n/)) {
        const pageMarker = line.match(/^--- الصفحة (\d+) ---$/);
        if (pageMarker) {
          paragraphs.push(new Paragraph({
            text: `الصفحة ${pageMarker[1]}`,
            heading: 'Heading2',
            bidirectional: true,
            alignment: 'right',
            spacing: { before: 180, after: 120 },
          }));
          continue;
        }
        const hasArabic = /[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]/.test(line);
        paragraphs.push(new Paragraph({
          text: line || ' ',
          bidirectional: hasArabic,
          alignment: hasArabic ? 'right' : 'left',
          spacing: { after: 80 },
        }));
      }
      const blob = await Packer.toBlob(new Document({ sections: [{ children: paragraphs }] }));
      const stem = localPreview.name.replace(/-(?:extracted|ocr)\.pdf\.txt$/i, '').replace(/\.pdf\.txt$/i, '');
      const filename = `${stem || 'docmo-pdf-text'}-extracted.docx`;
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت تجهيز ملف Word.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({
          filename,
          mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          data,
        });
        setNotice(`تم حفظ ${filename}. تم ضبط اتجاه الفقرات التي تحتوي على العربية من اليمين إلى اليسار؛ راجع النص الناتج يدويًا.`);
      } else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. راجع النص الناتج يدويًا؛ هذا تحويل للنص المستخرج وليس استعادة لتنسيق PDF الأصلي.`);
      }
    } catch (err) {
      setNotice(`تعذر إنشاء ملف Word: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Creates an Excel workbook from extracted PDF text. This is a text-to-rows bridge,
  // not a table-layout reconstruction engine; review columns and values after export.
  const exportExtractedPdfTextToXlsx = async () => {
    if (!localPreview || localPreview.kind !== 'text' || !/\.pdf\.txt$/i.test(localPreview.name)) return;
    try {
      const rows = localPreview.text.split(/\r?\n/).map(line => {
        const trimmed = line.trim();
        if (!trimmed) return [''];
        const page = trimmed.match(/^--- الصفحة (\d+) ---$/);
        if (page) return ['الصفحة', page[1]];
        if (trimmed.includes('\t')) return trimmed.split('\t').map(cell => cell.trim());
        if (/ {2,}/.test(trimmed)) return trimmed.split(/ {2,}/).map(cell => cell.trim());
        return [trimmed];
      });
      const blob = createXlsxBlob(rows);
      const stem = localPreview.name.replace(/-(?:extracted|ocr)\.pdf\.txt$/i, '').replace(/\.pdf\.txt$/i, '');
      const filename = `${stem || 'docmo-pdf-text'}-extracted.xlsx`;
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت تجهيز ملف Excel.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({ filename, mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', data });
        setNotice(`تم حفظ ${filename}. هذا الملف يرتب النص المستخرج في صفوف؛ راجع الأعمدة، فهو لا يعيد بناء جداول PDF الأصلية تلقائيًا.`);
      } else {
        const url = URL.createObjectURL(blob); const link = document.createElement('a');
        link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. راجع البيانات يدويًا؛ هذا ليس استعادة مضمونة لجداول PDF الأصلية.`);
      }
    } catch (err) { setNotice(`تعذر إنشاء ملف Excel: ${err instanceof Error ? err.message : String(err)}`); }
  };

  // Experimental positional PDF table extraction. It groups text spans by vertical
  // position and estimates cell boundaries from horizontal gaps; it is not a full table parser.
  const extractPdfTablesToXlsx = async (item: ConversionFile) => {
    let loadingTask: ReturnType<typeof pdfjsLib.getDocument> | undefined;
    try {
      if (item.file.size > 50 * 1024 * 1024) {
        setNotice('استخراج الجداول محدود بملفات PDF حتى 50 ميغابايت لتقليل استهلاك ذاكرة الهاتف.');
        return;
      }
      const bytes = new Uint8Array(await item.file.arrayBuffer());
      if (new TextDecoder('latin1').decode(bytes.subarray(0, 5)) !== '%PDF-') {
        throw new Error('الملف لا يحمل ترويسة PDF متوقعة.');
      }
      loadingTask = pdfjsLib.getDocument({ data: bytes, isEvalSupported: false, useSystemFonts: true, stopAtErrors: false });
      loadingTask.onPassword = (updatePassword, reason) => {
        const promptText = reason === pdfjsLib.PasswordResponses.NEED_PASSWORD
          ? 'هذا الملف محمي. أدخل كلمة المرور لاستخراج النص محليًا:'
          : 'كلمة المرور غير صحيحة. حاول مرة أخرى:';
        const password = window.prompt(promptText);
        if (password === null) {
          setNotice('أُلغي استخراج الجداول من الملف المحمي. لم يُرسل الملف إلى خادم.');
          void loadingTask?.destroy();
          return;
        }
        updatePassword(password);
      };
      const pdf = await loadingTask.promise;
      const maxPages = Math.min(pdf.numPages, 100);
      const rows: string[][] = [['رقم الصفحة', 'الخلية 1']];
      let maxColumns = 1;
      for (let pageNumber = 1; pageNumber <= maxPages; pageNumber++) {
        const page = await pdf.getPage(pageNumber);
        const content = await page.getTextContent();
        const spans = content.items.filter((entry): entry is typeof entry & { str: string; transform: number[]; width?: number; height?: number; hasEOL?: boolean } =>
          'str' in entry && typeof entry.str === 'string' && 'transform' in entry && entry.str.trim().length > 0
        ).map(entry => ({
          text: entry.str.trim(),
          x: Number(entry.transform[4] || 0),
          y: Number(entry.transform[5] || 0),
          width: Math.max(0, Number(entry.width || 0)),
          height: Math.max(1, Math.abs(Number(entry.height || entry.transform[3] || 10))),
        })).sort((a, b) => b.y - a.y || a.x - b.x);

        const lineGroups: Array<typeof spans> = [];
        for (const span of spans) {
          let line = lineGroups.find(group => Math.abs(group[0].y - span.y) <= Math.max(2.5, span.height * 0.45));
          if (!line) { line = []; lineGroups.push(line); }
          line.push(span);
        }
        lineGroups.sort((a, b) => b[0].y - a[0].y);
        for (const line of lineGroups) {
          line.sort((a, b) => a.x - b.x);
          const cells: string[] = [];
          let current = '';
          let previousEnd: number | undefined;
          let previousHeight = 10;
          for (const span of line) {
            const gap = previousEnd === undefined ? 0 : span.x - previousEnd;
            const boundary = current && gap > Math.max(14, previousHeight * 1.5);
            if (boundary) { cells.push(current.trim()); current = ''; }
            else if (current && gap > Math.max(2, previousHeight * 0.22) && !/\s$/.test(current)) current += ' ';
            current += span.text;
            previousEnd = span.x + span.width;
            previousHeight = span.height;
          }
          if (current.trim()) cells.push(current.trim());
          if (cells.length) {
            maxColumns = Math.max(maxColumns, cells.length);
            rows.push([String(pageNumber), ...cells]);
          }
        }
        page.cleanup();
      }
      await pdf.destroy();
      if (rows.length <= 1) {
        setNotice('لم يعثر PDF.js على نص قابل للاستخراج. قد يكون الملف ممسوحًا ضوئيًا ويحتاج OCR؛ لا يمكن لهذه الطريقة قراءة الجداول من الصور وحدها.');
        return;
      }
      rows[0] = ['رقم الصفحة', ...Array.from({ length: maxColumns }, (_, i) => `العمود ${i + 1}`)];
      const normalizedRows = rows.map((row, index) => index === 0 ? row : [row[0], ...row.slice(1), ...Array(Math.max(0, maxColumns - (row.length - 1))).fill('')]);
      const blob = createXlsxBlob(normalizedRows);
      const filename = item.file.name.replace(/\.pdf$/i, '') + '-tables-estimated.xlsx';
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذر تجهيز ملف Excel.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({ filename, mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', data });
        setNotice(`تم حفظ ${filename} من ${maxPages} صفحة كحد أقصى. تم تقدير الخلايا من المسافات بين النصوص؛ راجع الصفوف والأعمدة يدويًا لأن النتيجة ليست استعادة مضمونة للجداول الأصلية.`);
      } else {
        const url = URL.createObjectURL(blob); const link = document.createElement('a');
        link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. تقدير الخلايا تقريبي؛ راجع الأعمدة والقيم يدويًا قبل استخدامها.`);
      }
    } catch (err) {
      try { await loadingTask?.destroy(); } catch { /* best-effort cleanup */ }
      setNotice(`تعذر استخراج الجداول إلى Excel: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Export extracted PDF/OCR text to UTF-8 CSV with a BOM for better Arabic compatibility
  // in spreadsheet apps. This is a line-preserving bridge, not a table reconstruction.
  const exportExtractedPdfTextToCsv = async () => {
    if (!localPreview || localPreview.kind !== 'text' || !/\.pdf\.txt$/i.test(localPreview.name)) return;
    try {
      const csvEscape = (value: string) => `"${value.replace(/"/g, '""')}"`;
      const rows: string[][] = [['رقم الصفحة', 'النص']];
      let pageNumber = '';
      for (const line of localPreview.text.split(/\r?\n/)) {
        const marker = line.match(/^--- الصفحة (\d+) ---$/);
        if (marker) {
          pageNumber = marker[1];
          continue;
        }
        rows.push([pageNumber, line]);
      }
      const csv = '\uFEFF' + rows.map(row => row.map(csvEscape).join(',')).join('\r\n');
      const filename = localPreview.name.replace(/-(?:extracted|ocr)\.pdf\.txt$/i, '').replace(/\.pdf\.txt$/i, '') + '-extracted.csv';
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذر تجهيز ملف CSV.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({ filename, mimeType: 'text/csv;charset=utf-8', data });
        setNotice(`تم حفظ ${filename}. حُفظ كل سطر في صف مع رقم الصفحة لدعم مراجعة النص العربي؛ لم تتم إعادة بناء الجداول الأصلية.`);
      } else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. يحتوي النص المستخرج كسطور قابلة للمراجعة في Excel؛ راجع ترتيب النص العربي يدويًا.`);
      }
    } catch (err) {
      setNotice(`تعذر إنشاء ملف CSV: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  const saveExtractedPdfText = async () => {
    if (!localPreview || localPreview.kind !== 'text' || !/\.pdf\.txt$/i.test(localPreview.name)) return;
    try {
      const filename = localPreview.name;
      const blob = new Blob([localPreview.text], { type: 'text/plain;charset=utf-8' });
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت تجهيز الملف النصي.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(blob);
        });
        await NativeSaveFile.saveFile({ filename, mimeType: 'text/plain', data });
        setNotice(`تم حفظ النص المستخرج باسم ${filename}. يرجى مراجعته لأنه استخراج تجريبي.`);
      } else {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a'); link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل ${filename}. يرجى مراجعة النص المستخرج قبل الاعتماد عليه.`);
      }
    } catch (err) { setNotice(err instanceof Error ? err.message : 'تعذر حفظ النص المستخرج.'); }
  };

  const inspectPdf = async (item: ConversionFile) => {
    try {
      // Read only a bounded amount for a quick, local diagnostic; this is not a PDF parser.
      const sampleSize = Math.min(item.file.size, 12 * 1024 * 1024);
      const bytes = new Uint8Array(await item.file.slice(0, sampleSize).arrayBuffer());
      const sample = new TextDecoder('latin1').decode(bytes);
      const isPdf = sample.startsWith('%PDF-');
      const encryptedMarker = /\/Encrypt\b/.test(sample);
      const hasToUnicode = /\/ToUnicode\b/.test(sample);
      const imageObjects = (sample.match(/\/Subtype\s*\/Image\b/g) || []).length;
      const pageMarkers = (sample.match(/\/Type\s*\/Page\b/g) || []).length;
      const hasArabicLiteral = /[\u0600-\u06FF]/.test(sample);
      const findings = [
        `ترويسة PDF: ${isPdf ? 'صحيحة مبدئيًا' : 'لم يتم التعرف عليها'}`,
        `علامة تشفير ظاهرة: ${encryptedMarker ? 'نعم — قد يتطلب الملف كلمة مرور أو صلاحيات إضافية' : 'لم تُكتشف في الجزء المفحوص'}`,
        `خرائط Unicode للخطوط: ${hasToUnicode ? 'موجودة في الجزء المفحوص' : 'لم تُكتشف في الجزء المفحوص'}`,
        `علامات صور تقريبية: ${imageObjects}`,
        `علامات صفحات تقريبية: ${pageMarkers}`,
        `محارف عربية ظاهرة في البيانات الخام: ${hasArabicLiteral ? 'نعم' : 'لم تُكتشف؛ قد تكون النصوص مضغوطة أو مرمّزة'}`,
      ];
      const limitNote = item.file.size > sampleSize ? ' فُحص أول 12 ميغابايت فقط لتقليل استهلاك ذاكرة الهاتف.' : '';
      setNotice(`فحص PDF محلي لملف ${item.file.name}: ${findings.join(' • ')}.${limitNote} هذا فحص استدلالي وليس تحققًا كاملًا من الملف أو ضمانًا لدقة التحويل.`);
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'تعذر فحص ملف PDF محليًا.');
    }
  };

  const openOfficeDocumentExternally = async (item: ConversionFile) => {
    if (!Capacitor.isNativePlatform()) {
      setNotice('لفتح هذا المستند في تطبيق خارجي، افتح الملف المحفوظ من مدير الملفات في هاتفك.');
      return;
    }
    if (item.file.size > 40 * 1024 * 1024) {
      setNotice('حجم الملف أكبر من 40 ميغابايت. احفظه أولًا ثم افتحه من مدير الملفات لتقليل استهلاك ذاكرة الهاتف.');
      return;
    }
    const extension = item.file.name.split('.').pop()?.toLowerCase() || '';
    const mimeTypes: Record<string, string> = {
      doc: 'application/msword',
      docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      xls: 'application/vnd.ms-excel',
      xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      ppt: 'application/vnd.ms-powerpoint',
      pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      rtf: 'application/rtf',
      csv: 'text/csv',
      txt: 'text/plain',
      odt: 'application/vnd.oasis.opendocument.text',
      ods: 'application/vnd.oasis.opendocument.spreadsheet',
      odp: 'application/vnd.oasis.opendocument.presentation',
    };
    const mimeType = item.file.type || mimeTypes[extension] || 'application/octet-stream';
    try {
      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onerror = () => reject(new Error('تعذرت قراءة بيانات المستند.'));
        reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
        reader.readAsDataURL(item.file);
      });
      await NativeOpenDocument.openDocument({ filename: item.file.name, mimeType, data });
      setNotice('تم إرسال المستند إلى تطبيق متوافق على الهاتف. قد تحتاج إلى تثبيت تطبيق يدعم هذه الصيغة.');
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'تعذر فتح المستند في تطبيق خارجي.');
    }
  };

  const openPdfPreview = async (item: ConversionFile) => {
    if (!item.previewUrl) return;
    if (Capacitor.isNativePlatform()) {
      try {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت قراءة ملف PDF'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(item.file);
        });
        await NativeOpenPdf.openPdf({ filename: item.file.name, data });
        setNotice('تم فتح ملف PDF في عارض المستندات المتوفر على الهاتف.');
      } catch (err) {
        setNotice(err instanceof Error ? err.message : 'تعذر فتح PDF. تأكد من وجود تطبيق لعرض PDF.');
      }
    } else {
      setPreviewFile(item);
    }
  };

  const exportAccessCopy = async () => {
    if (!localPreview || localPreview.kind !== 'access') return;
    const source = files.find(item => item.file.name === localPreview.name)?.file;
    if (!source) { setNotice('تعذر العثور على الملف الأصلي في قائمة الملفات.'); return; }
    try {
      const filename = source.name.replace(/\.(mdb|accdb)$/i, '') + '-copy.' + source.name.split('.').pop()!.toLowerCase();
      if (Capacitor.isNativePlatform()) {
        const data = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onerror = () => reject(new Error('تعذرت قراءة ملف قاعدة البيانات.'));
          reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
          reader.readAsDataURL(source);
        });
        await NativeSaveFile.saveFile({ filename, mimeType: 'application/octet-stream', data });
        setNotice(`تم حفظ نسخة مطابقة باسم ${filename}. لم يتم تعديل قاعدة البيانات الأصلية.`);
      } else {
        const url = URL.createObjectURL(source);
        const link = document.createElement('a'); link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1500);
        setNotice(`بدأ تنزيل النسخة ${filename}. هذه نسخة مطابقة وليست تصديرًا للجداول.`);
      }
    } catch (err) { setNotice(err instanceof Error ? err.message : 'تعذر حفظ نسخة من ملف Access.'); }
  };

  const openTextOrCsvPreview = async (item: ConversionFile) => {
    const ext = item.file.name.split('.').pop()?.toLowerCase();
    if (ext === 'mdb' || ext === 'accdb') {
      try {
        const header = new Uint8Array(await item.file.slice(0, 32).arrayBuffer());
        const signature = new TextDecoder('ascii').decode(header.slice(4, 20)).replace(/\0/g, '').trim();
        const expected = ext === 'mdb' ? /Standard Jet DB/i.test(signature) : /Standard ACE DB/i.test(signature);
        setLocalPreview({
          name: item.file.name, kind: 'access', text: '', rows: [],
          accessInfo: { format: ext.toUpperCase(), size: item.file.size, signature: signature || 'غير معروف', recognized: expected },
        });
        setNotice(expected ? `تم التعرف مبدئيًا على ملف ${ext.toUpperCase()} محليًا.` : 'تم فتح معلومات الملف، لكن ترويسة قاعدة البيانات غير مطابقة للنمط المتوقع.');
      } catch { setNotice('تعذرت قراءة معلومات ملف Access.'); }
      return;
    }
    if (ext === 'docx') { try { await previewDocx(item); } catch (err) { setNotice(err instanceof Error ? err.message : 'تعذرت معاينة ملف Word.'); } return; }
    if (ext === 'xlsx') { try { await previewXlsx(item); } catch (err) { setNotice(err instanceof Error ? err.message : 'تعذرت معاينة ملف Excel.'); } return; }
    if (ext !== 'csv' && ext !== 'txt') return;
    try {
      const text = await item.file.text();
      if (ext === 'csv') {
        const firstLine = text.split(/\r?\n/, 1)[0] || '';
        const candidates = [',', ';', '\t'];
        const delimiter = candidates.sort((a, b) => firstLine.split(b).length - firstLine.split(a).length)[0];
        const rows: string[][] = [];
        let row: string[] = [];
        let cell = '';
        let quoted = false;
        for (let i = 0; i < text.length; i++) {
          const ch = text[i];
          if (ch === '"' && quoted && text[i + 1] === '"') { cell += '"'; i++; }
          else if (ch === '"') quoted = !quoted;
          else if (ch === delimiter && !quoted) { row.push(cell); cell = ''; }
          else if ((ch === '\n' || ch === '\r') && !quoted) {
            if (ch === '\r' && text[i + 1] === '\n') i++;
            row.push(cell); cell = '';
            if (row.some(value => value.length > 0)) rows.push(row);
            row = [];
          } else cell += ch;
        }
        if (cell.length || row.length) { row.push(cell); if (row.some(value => value.length > 0)) rows.push(row); }
        setLocalPreview({ name: item.file.name, kind: 'csv', text: '', rows });
        setNotice(rows.length > 500 ? `تم فتح CSV محليًا؛ ستُعرض أول 500 صف، ويمكن تصدير جميع الصفوف (${rows.length}) إلى Excel.` : 'تم فتح ملف CSV محليًا دون رفعه إلى الإنترنت.');
      } else {
        setLocalPreview({ name: item.file.name, kind: 'text', text, rows: [] });
        setNotice('تم فتح الملف النصي محليًا دون رفعه إلى الإنترنت.');
      }
    } catch {
      setNotice('تعذرت قراءة الملف. جرّب ترميز UTF-8 أو ملفًا أصغر.');
    }
  };


  // Minimal ZIP reader for DOCX/XLSX Office Open XML files. It extracts only the
  // XML parts needed for local preview; it does not upload the selected file.
  const readZipEntry = async (file: File, wantedName: string): Promise<string> => {
    const bytes = new Uint8Array(await file.arrayBuffer());
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let eocd = -1;
    for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
      if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('ملف Office غير صالح أو غير مدعوم.');
    const count = view.getUint16(eocd + 10, true);
    let offset = view.getUint32(eocd + 16, true);
    for (let n = 0; n < count; n++) {
      if (view.getUint32(offset, true) !== 0x02014b50) break;
      const method = view.getUint16(offset + 10, true);
      const compressedSize = view.getUint32(offset + 20, true);
      const nameLength = view.getUint16(offset + 28, true);
      const extraLength = view.getUint16(offset + 30, true);
      const commentLength = view.getUint16(offset + 32, true);
      const localOffset = view.getUint32(offset + 42, true);
      const name = new TextDecoder().decode(bytes.slice(offset + 46, offset + 46 + nameLength));
      if (name === wantedName) {
        if (view.getUint32(localOffset, true) !== 0x04034b50) throw new Error('تعذر قراءة جزء من الملف.');
        const localNameLength = view.getUint16(localOffset + 26, true);
        const localExtraLength = view.getUint16(localOffset + 28, true);
        const start = localOffset + 30 + localNameLength + localExtraLength;
        const compressed = bytes.slice(start, start + compressedSize);
        let output: Uint8Array;
        if (method === 0) output = compressed;
        else if (method === 8) {
          if (typeof DecompressionStream === 'undefined') throw new Error('معاينة ملفات Office تتطلب إصدار Android WebView أحدث.');
          const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream('deflate-raw' as CompressionFormat));
          output = new Uint8Array(await new Response(stream).arrayBuffer());
        } else throw new Error('طريقة ضغط الملف غير مدعومة.');
        return new TextDecoder('utf-8').decode(output);
      }
      offset += 46 + nameLength + extraLength + commentLength;
    }
    throw new Error(`لم يتم العثور على الجزء المطلوب: ${wantedName}`);
  };

  const previewDocx = async (item: ConversionFile) => {
    const xml = await readZipEntry(item.file, 'word/document.xml');
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    if (doc.querySelector('parsererror')) throw new Error('تعذر تحليل محتوى ملف Word.');
    const body = doc.getElementsByTagName('w:body')[0];
    if (!body) throw new Error('لم يتم العثور على نص داخل ملف Word.');
    const blocks: Array<{ type: 'paragraph' | 'table'; text?: string; rows?: string[][] }> = [];
    for (const child of Array.from(body.children)) {
      if (child.localName === 'p') {
        const text = Array.from(child.getElementsByTagName('w:t')).map(n => n.textContent || '').join('');
        if (text) blocks.push({ type: 'paragraph', text });
      } else if (child.localName === 'tbl') {
        const rows = Array.from(child.getElementsByTagName('w:tr')).map(tr =>
          Array.from(tr.children).filter(cell => cell.localName === 'tc').map(cell =>
            Array.from(cell.getElementsByTagName('w:t')).map(n => n.textContent || '').join('')
          )
        );
        if (rows.length) blocks.push({ type: 'table', rows: rows.slice(0, 500) });
      }
    }
    setLocalPreview({ name: item.file.name, kind: 'docx', text: '', rows: [], blocks });
    setNotice('تمت معاينة النصوص والجداول الأساسية من DOCX محليًا. قد لا يظهر التنسيق الأصلي بالكامل.');
  };

  const previewXlsx = async (item: ConversionFile) => {
    const originalWorkbook = await item.file.arrayBuffer();
    const workbook = XLSX.read(originalWorkbook, { type: 'array', cellDates: false });
    const firstSheetName = workbook.SheetNames[0];
    if (!firstSheetName) throw new Error('لا توجد أوراق عمل في هذا الملف.');
    const sheet = workbook.Sheets[firstSheetName];
    const rawRows = XLSX.utils.sheet_to_json<unknown[]>(sheet, { header: 1, defval: '', raw: false });
    const normalizeCell = (value: unknown) => value == null ? '' : String(value);
    const allRows = rawRows.map(row => row.map(normalizeCell));
    const rows = allRows.slice(0, 500);
    setLocalPreview({
      name: item.file.name,
      kind: 'xlsx',
      text: '',
      rows,
      originalRows: rows.map(row => [...row]),
      originalWorkbook,
      originalSheetName: firstSheetName,
    });
    setNotice(`تمت معاينة الورقة «${firstSheetName}» محليًا. عند الحفظ ستُحدّث الخلايا التي تغيّرت داخل المصنف الأصلي مع إبقاء الأوراق الأخرى؛ قد تتغير بعض خصائص التنسيق غير المدعومة من محرك XLSX.`);
  };

  const removeFile = (id: string) => {
    const found = files.find(f => f.id === id);
    if (found?.previewUrl) URL.revokeObjectURL(found.previewUrl);
    if (previewFile?.id === id) setPreviewFile(null);
    setFiles(prev => prev.filter(f => f.id !== id));
  };

  const processFile = async (item: ConversionFile) => {
    if (!/\.pdf$/i.test(item.file.name)) {
      setFiles(prev => prev.map(f => f.id === item.id ? {
        ...f, status: 'error',
        error: 'التحويل الحالي يدعم PDF فقط. دعم DOCX وXLSX وAccess يحتاج إلى محركات إضافية.'
      } : f));
      return;
    }
    if (localOnlyMode) {
      setNotice('وضع العمل المحلي فقط مفعّل: لم يتم رفع الملف. استخدم أزرار استخراج النص أو OCR أو استخراج الجداول المحلية، ثم صدّر النتيجة إلى Word أو Excel. التحويل الذكي عبر الخادم معطّل في هذا الوضع.');
      return;
    }
    try {
      setFiles(prev => prev.map(f => f.id === item.id ? { ...f, status: 'processing', progress: 10 } : f));

      // 1. Convert file to base64
      const base64 = await new Promise<string>((resolve) => {
        const reader = new FileReader();
        reader.onload = () => resolve((reader.result as string).split(',')[1]);
        reader.readAsDataURL(item.file);
      });

      setFiles(prev => prev.map(f => f.id === item.id ? { ...f, progress: 30 } : f));

      // 2. Call Secure Backend processing
      const response = await fetch('/api/process-pdf', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ base64 })
      });

      if (!response.ok) throw new Error("AI processing failed on server");
      const { result } = await response.json();

      let rawPayload;
      try {
        const cleanJson = result.replace(/```json/g, '').replace(/```/g, '').trim();
        rawPayload = JSON.parse(cleanJson);
      } catch (e) {
        console.error("Parse error:", e, result);
        throw new Error("Linguistic analysis and structure failed. Please try again.");
      }

      const structuredContent = rawPayload.structure || [];
      const linguistInsights = rawPayload.insights;

      setFiles(prev => prev.map(f => f.id === item.id ? { ...f, progress: 70 } : f));

      // 3. Call Backend to generate DOCX
      const backendResponse = await fetch('/api/generate-docx', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          filename: item.file.name.replace('.pdf', ''),
          content: structuredContent
        })
      });

      if (!backendResponse.ok) throw new Error("Backend generation failed");

      const blob = await backendResponse.blob();
      const url = URL.createObjectURL(blob);

      setFiles(prev => prev.map(f => f.id === item.id ? { 
        ...f, 
        status: 'completed', 
        progress: 100,
        resultUrl: url,
        insights: linguistInsights
      } : f));

      // Instant Download
      const link = document.createElement('a');
      link.href = url;
      link.download = item.file.name.replace('.pdf', '.docx');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 }
      });

    } catch (err) {
      console.error(err);
      setFiles(prev => prev.map(f => f.id === item.id ? { 
        ...f, 
        status: 'error', 
        error: err instanceof Error ? err.message : "Conversion failed" 
      } : f));
    }
  };

  return (
    <div className="min-h-screen bg-[#0A0A0B] text-[#E0E0E0] font-sans selection:bg-blue-500/30 antialiased">
      {/* Header */}
      <header className="border-b border-white/10 bg-[#0A0A0B]/80 backdrop-blur-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 md:px-8 h-20 flex items-center justify-between">
          <div className="flex flex-col">
            <h1 className="font-serif text-2xl md:text-3xl italic tracking-tight leading-none text-white">docmo <span className="text-blue-400">Documents</span></h1>
            <p className="text-[9px] md:text-[10px] uppercase tracking-[0.3em] text-[#888] mt-1 text-left">Document tools</p>
          </div>
          <nav className="flex items-center gap-3 md:gap-12 text-[10px] md:text-[11px] uppercase tracking-[0.1em] md:tracking-[0.2em] font-medium">
            <div className="flex items-center gap-1.5 md:gap-2 text-blue-400">
              <Languages className="w-3 md:w-3.5 h-3 md:h-3.5" />
              <span className="hidden sm:inline">إدارة الملفات</span>
            </div>
            <div className="flex items-center gap-1.5 md:gap-2 text-purple-400">
              <Sparkles className="w-3 md:w-3.5 h-3 md:h-3.5" />
              <span className="hidden sm:inline">تحويل PDF إلى Word</span>
            </div>
            <div className="flex items-center gap-1.5 md:gap-2 opacity-40 hover:opacity-100 transition-opacity cursor-default">
              <ShieldCheck className="w-3 md:w-3.5 h-3 md:h-3.5" />
              <span className="hidden sm:inline">معاينة PDF محلية</span>
            </div>
          </nav>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 md:px-8 py-6 md:py-10">
        <section className={`mb-5 flex flex-col gap-3 rounded-xl border p-4 md:flex-row md:items-center md:justify-between ${localOnlyMode ? 'border-emerald-500/40 bg-emerald-950/30' : 'border-white/10 bg-white/[0.03]'}`} aria-label="إعدادات الاتصال">
          <div className="flex items-start gap-3">
            <ShieldCheck className={`mt-0.5 h-5 w-5 shrink-0 ${localOnlyMode ? 'text-emerald-400' : 'text-blue-300'}`} />
            <div>
              <p className="text-sm font-semibold text-white">{localOnlyMode ? 'وضع محلي فقط مفعّل' : 'التحويل الذكي يحتاج إلى اتصال'}</p>
              <p className="mt-1 text-xs leading-5 text-gray-400">{localOnlyMode ? 'طلبات التحويل إلى الخادم محظورة. المعاينة والاستخراج المحلي متاحان بحسب التبعيات المثبتة.' : 'ميزة التحويل الذكي ترسل محتوى PDF إلى خادم المعالجة. يمكنك تفعيل الوضع المحلي لمنع ذلك.'}</p>
            </div>
          </div>
          <button type="button" onClick={() => { setLocalOnlyMode(value => !value); setNotice(localOnlyMode ? 'تم إيقاف الوضع المحلي فقط. التحويل عبر الخادم قد يرسل ملف PDF إلى خدمة المعالجة.' : 'تم تفعيل الوضع المحلي فقط. لن يُرسل ملف PDF إلى خادم التحويل الذكي عبر زر التحويل.'); }} className={`rounded-lg px-4 py-2 text-xs font-bold transition-colors ${localOnlyMode ? 'bg-emerald-500/20 text-emerald-200 hover:bg-emerald-500/30' : 'bg-white/10 text-white hover:bg-white/15'}`}>
            {localOnlyMode ? 'السماح بالتحويل عبر الخادم' : 'تفعيل الوضع المحلي فقط'}
          </button>
        </section>
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 lg:gap-8 items-start">
          
          <div className="col-span-12 lg:col-span-4 flex flex-col gap-6 order-2 lg:order-1">
            <div className="flex items-center justify-between">
              <h2 className="text-[10px] font-bold uppercase tracking-[0.3em] text-[#888]">ملفاتك ({files.length})</h2>
            </div>

            <div className="flex flex-col gap-3">
              <AnimatePresence mode="popLayout">
                {files.length === 0 ? (
                  <div className="glass p-8 rounded-2xl text-center border-dashed border-2 border-white/5">
                    <p className="text-xs text-[#888] uppercase tracking-widest leading-loose">لا توجد ملفات بعد.<br/>اختر ملفات من هاتفك.</p>
                  </div>
                ) : (
                  files.map((item) => (
                    <motion.div 
                      key={item.id}
                      layout
                      initial={{ opacity: 0, x: -20 }}
                      animate={item.status === 'processing' ? { 
                        opacity: 1, 
                        x: 0,
                        backgroundColor: "rgba(59, 130, 246, 0.05)",
                        boxShadow: "0 0 20px rgba(59, 130, 246, 0.1)"
                      } : { 
                        opacity: 1, 
                        x: 0,
                        backgroundColor: "rgba(255, 255, 255, 0.03)",
                        boxShadow: "none"
                      }}
                      transition={item.status === 'processing' ? {
                        backgroundColor: { duration: 1.5, repeat: Infinity, repeatType: "mirror" },
                        boxShadow: { duration: 1.5, repeat: Infinity, repeatType: "mirror" }
                      } : { duration: 0.2 }}
                      exit={{ opacity: 0, scale: 0.95 }}
                      className={`glass p-5 rounded-xl flex flex-col gap-3 group transition-all ${item.status === 'processing' ? 'border-l-2 border-blue-500' : 'border-l-2 border-transparent'}`}
                    >
                      <div className="flex items-center justify-between w-full">
                        <div className="flex-1 min-w-0 pr-4 text-left">
                          <span className={`block text-sm font-medium truncate mb-1 ${item.status === 'error' ? 'text-red-400' : 'text-white'}`}>
                            {item.file.name}
                          </span>
                          <div className="flex items-center gap-2 overflow-hidden text-ellipsis whitespace-nowrap">
                            <span className="text-[9px] text-[#888] uppercase tracking-wider font-bold">
                              {(item.file.size / 1024 / 1024).toFixed(1)} MB
                            </span>
                            <span className="text-[8px] opacity-20">•</span>
                            <span className="text-[9px] text-[#888] uppercase tracking-wider font-bold truncate">
                              {item.status === 'completed' ? 'Synthesis Complete' : item.status === 'processing' ? `Analysing Neural Layers` : item.status === 'error' ? 'Failure' : 'Ready'}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center gap-1">
                          {item.previewUrl && (
                            <button
                              onClick={() => inspectPdf(item)}
                              title="فحص PDF والنصوص العربية محليًا"
                              aria-label="فحص PDF والنصوص العربية محليًا"
                              className="p-2 text-white hover:text-blue-400 transition-colors"
                            >
                              <SearchCheck className="w-4 h-4" />
                            </button>
                          )}
                          {item.previewUrl && (
                            <button
                              onClick={() => extractPdfTextLocally(item)}
                              title="محاولة استخراج النص من PDF محليًا (تجريبية)"
                              aria-label="استخراج نص PDF محليًا"
                              className="p-2 text-white hover:text-emerald-400 transition-colors"
                            >
                              <Languages className="w-4 h-4" />
                            </button>
                          )}
                          {item.previewUrl && (
                            <button
                              onClick={() => extractPdfTablesToXlsx(item)}
                              title="استخراج تقريبي لجداول PDF إلى Excel (تجريبي)"
                              aria-label="استخراج جداول PDF إلى Excel"
                              className="p-2 text-white hover:text-emerald-400 transition-colors"
                            >
                              <Files className="w-4 h-4" />
                            </button>
                          )}
                          {item.previewUrl && (
                            <button
                              onClick={() => runLocalPdfOcr(item)}
                              title="OCR تجريبي محلي للنصوص المصوّرة بالعربية والإنجليزية"
                              aria-label="استخراج النص من PDF المصوّر باستخدام OCR"
                              className="p-2 text-white hover:text-amber-400 transition-colors"
                            >
                              <Sparkles className="w-4 h-4" />
                            </button>
                          )}
                          {item.previewUrl && (
                            <button
                              onClick={() => openPdfPreview(item)}
                              title="فتح PDF في العارض"
                              className="p-2 text-white hover:text-blue-400 transition-colors"
                            >
                              <FileText className="w-4 h-4" />
                            </button>
                          )}
                          {/\.(doc|docx|xls|xlsx|ppt|pptx|rtf|odt|ods|odp)$/i.test(item.file.name) && (
                            <button
                              onClick={() => openOfficeDocumentExternally(item)}
                              title="فتح المستند في تطبيق متوافق على الهاتف"
                              aria-label="فتح المستند في تطبيق خارجي"
                              className="p-2 text-white hover:text-amber-400 transition-colors"
                            >
                              <ArrowRight className="w-4 h-4" />
                            </button>
                          )}
                          {/\.(csv|txt|docx|xlsx)$/i.test(item.file.name) && (
                            <button
                              onClick={() => openTextOrCsvPreview(item)}
                              title="معاينة الملف محليًا"
                              aria-label="معاينة الملف محليًا"
                              className="p-2 text-white hover:text-blue-400 transition-colors"
                            >
                              <Files className="w-4 h-4" />
                            </button>
                          )}
                          {item.status === 'completed' && item.resultUrl && (
                            <button
                              onClick={() => saveConvertedFile(item)}
                              title="حفظ نسخة Word في الهاتف"
                              aria-label="حفظ نسخة Word في الهاتف"
                              className="p-2 text-white hover:text-blue-400 transition-colors"
                            >
                              <Download className="w-4 h-4" />
                            </button>
                          )}
                          {item.status === 'idle' && /\.pdf$/i.test(item.file.name) && (
                            <button
                              onClick={() => processFile(item)}
                              title="تحويل PDF إلى Word (يتطلب الخادم)"
                              className="p-2 text-white hover:text-blue-400 transition-colors"
                            >
                              <Zap className="w-4 h-4 fill-current" />
                            </button>
                          )}
                          <button 
                            onClick={() => removeFile(item.id)}
                            className="p-2 text-[#888] hover:text-red-500 transition-colors"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      {(item.status === 'processing' || item.status === 'completed') && (
                        <div className="w-full bg-white/5 rounded-full h-1 overflow-hidden relative">
                          <motion.div 
                            initial={{ width: 0 }}
                            animate={{ width: `${item.progress}%` }}
                            className={`h-full rounded-full relative ${item.status === 'completed' ? 'bg-green-500/50' : 'bg-blue-500'}`}
                          >
                            {item.status === 'processing' && (
                              <motion.div 
                                animate={{ x: ['-100%', '200%'] }}
                                transition={{ duration: 1.5, repeat: Infinity, ease: "linear" }}
                                className="absolute inset-0 bg-gradient-to-r from-transparent via-white/30 to-transparent w-1/2"
                              />
                            )}
                          </motion.div>
                        </div>
                      )}

                      {/* Linguist Feature Section */}
                      {item.status === 'completed' && item.insights && (
                        <motion.div 
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: "auto", opacity: 1 }}
                          className="mt-2 pt-4 border-t border-white/5 flex flex-col gap-4 overflow-hidden"
                        >
                          <div className="flex items-center gap-2">
                            <BrainCircuit className="w-3.5 h-3.5 text-blue-400" />
                            <span className="text-[10px] font-bold uppercase tracking-[0.2em] text-[#888]">Linguist Insights</span>
                          </div>
                          
                          <div className="grid grid-cols-2 gap-3">
                            <div className="bg-white/3 rounded-lg p-3 border border-white/5">
                              <div className="flex items-center gap-1.5 mb-1 opacity-50">
                                <Languages className="w-3 h-3" />
                                <span className="text-[8px] uppercase tracking-wider font-semibold">Languages</span>
                              </div>
                              <p className="text-[11px] font-medium text-white/90">
                                {item.insights.primaryLanguage}
                                {item.insights.secondaryLanguages?.length > 0 && 
                                  <span className="text-blue-400 font-bold ml-1">
                                    (+{item.insights.secondaryLanguages.join(', ')})
                                  </span>
                                }
                              </p>
                            </div>
                            <div className="bg-white/3 rounded-lg p-3 border border-white/5">
                              <div className="flex items-center gap-1.5 mb-1 opacity-50">
                                <Target className="w-3 h-3" />
                                <span className="text-[8px] uppercase tracking-wider font-semibold">Profile</span>
                              </div>
                              <p className="text-[11px] font-medium text-white/90 capitalize">
                                {item.insights.tone} • {item.insights.complexity}
                              </p>
                            </div>
                          </div>

                          <div className="bg-white/3 rounded-lg p-3 border border-white/5">
                            <div className="flex items-center gap-1.5 mb-1 opacity-50">
                              <MessageSquareQuote className="w-3 h-3" />
                              <span className="text-[8px] uppercase tracking-wider font-semibold">Synthesis Summary</span>
                            </div>
                            <p className="text-[11px] leading-relaxed text-[#BBB] italic italic-font">
                              "{item.insights.summary}"
                            </p>
                          </div>
                        </motion.div>
                      )}
                    </motion.div>
                  ))
                )}
              </AnimatePresence>
            </div>
          </div>

          <div className="col-span-12 lg:col-span-8 flex flex-col gap-8 order-1 lg:order-2">
            <motion.div 
              whileHover={{ scale: 1.005 }}
              whileTap={{ scale: 0.995 }}
              onClick={() => fileInputRef.current?.click()}
              className="flex-1 glass rounded-2xl md:rounded-3xl border-dashed border-2 border-white/5 flex flex-col items-center justify-center p-8 md:p-16 gap-6 relative overflow-hidden group cursor-pointer hover:border-blue-500/30 transition-all min-h-[300px] md:min-h-[400px]"
            >
              <div className="absolute inset-0 bg-gradient-to-tr from-blue-500/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity"></div>
              
              <div className="w-16 h-16 md:w-24 md:h-24 rounded-full border border-white/10 flex items-center justify-center mb-2 group-hover:scale-110 group-hover:border-blue-500/50 transition-all duration-500">
                <Upload className="w-6 h-6 md:w-8 md:h-8 text-white/50 group-hover:text-blue-400 transition-colors" />
              </div>

              <div className="text-center z-10 space-y-2">
                <h3 className="text-2xl md:text-3xl font-light tracking-tight text-white mb-2 underline decoration-white/10 underline-offset-8">إضافة مستندات</h3>
                <p className="text-xs md:text-sm text-[#888] uppercase tracking-[0.2em] font-medium">PDF وWord وExcel وPowerPoint وAccess — إدارة الملفات</p>
              </div>

              <div className="flex flex-col sm:flex-row gap-4 z-10 mt-4">
                <button className="px-8 md:px-10 py-3 bg-white text-black text-[10px] md:text-[11px] font-black uppercase tracking-[0.2em] rounded-full btn-glow hover:scale-105 transition-transform active:scale-95 w-full sm:w-auto">
                  اختيار ملفات
                </button>
                <input 
                  type="file" 
                  multiple 
                  accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,.txt,.ppt,.pptx,.mdb,.accdb" 
                  className="hidden" 
                  ref={fileInputRef}
                  onChange={onFileChange}
                />
              </div>
            </motion.div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="glass rounded-2xl p-6 flex flex-col gap-4">
                <h4 className="text-[10px] uppercase tracking-[0.3em] text-[#888] font-bold">Neural Engine</h4>
                <div className="space-y-3">
                  <div className="flex items-center gap-3 text-xs opacity-70">
                    <CheckCircle2 className="w-3.5 h-3.5 text-blue-500" />
                    <span>Layout Reconstruction</span>
                  </div>
                  <div className="flex items-center gap-3 text-xs opacity-70">
                    <CheckCircle2 className="w-3.5 h-3.5 text-blue-500" />
                    <span>Hierarchy Detection</span>
                  </div>
                </div>
              </div>

              <div className="glass rounded-2xl p-6 flex flex-col gap-4">
                <h4 className="text-[10px] uppercase tracking-[0.3em] text-[#888] font-bold">Linguistics</h4>
                <div className="flex flex-wrap gap-1.5">
                  <span className="bg-white/10 text-[9px] font-black tracking-widest px-2.5 py-1.5 rounded-lg border border-white/5">ENGLISH</span>
                  <span className="bg-white/10 text-[9px] font-black tracking-widest px-2.5 py-1.5 rounded-lg border border-white/5 text-blue-400">ARABIC (RTL)</span>
                  <span className="bg-white/5 text-[9px] font-black tracking-widest px-2.5 py-1.5 rounded-lg border border-white/5 text-[#666]">MULTI</span>
                </div>
              </div>

              <div className="glass rounded-2xl p-6 flex flex-col justify-between">
                <div>
                  <h4 className="text-[10px] uppercase tracking-[0.3em] text-[#888] font-bold mb-4">Output Artifact</h4>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-light">MS Word</span>
                    <span className="text-[10px] text-[#666] font-bold">DOCX</span>
                  </div>
                  <div className="h-1 w-full bg-white/5 rounded-full overflow-hidden">
                    <motion.div 
                      initial={{ width: 0 }}
                      animate={{ width: "33%" }}
                      className="h-full bg-blue-400"
                    />
                  </div>
                  <div className="flex justify-between mt-2 text-[8px] uppercase tracking-widest text-[#555] font-black">
                    <span>Precise</span>
                    <span>Adaptive</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>

      {notice && (
        <div className="max-w-7xl mx-auto px-4 md:px-8 mt-4">
          <div className="glass rounded-xl p-4 text-sm text-blue-100" role="status">{notice}</div>
        </div>
      )}

      {localPreview && (
        <div className="fixed inset-0 z-50 bg-black/90 p-3 md:p-8 flex flex-col" role="dialog" aria-modal="true" aria-label="معاينة ملف محلي">
          <div className="flex items-center justify-between gap-3 pb-3">
            <div className="min-w-0">
              <h2 className="text-white font-semibold truncate">{localPreview.name}</h2>
              <p className="text-xs text-gray-400">معاينة محلية على الجهاز — لا يتم رفع محتوى الملف إلى الخادم</p>
            </div>
            <div className="flex items-center gap-2">
              {localPreview.kind === 'text' && /\.pdf\.txt$/i.test(localPreview.name) && (
                <>
                  <button onClick={exportExtractedPdfTextToDocx} className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white">تصدير إلى Word</button>
                  <button onClick={exportExtractedPdfTextToXlsx} className="rounded-lg bg-emerald-700 px-3 py-2 text-sm text-white">تصدير النص إلى Excel</button>
                  <button onClick={exportExtractedPdfTextToCsv} className="rounded-lg bg-teal-700 px-3 py-2 text-sm text-white">حفظ النص كـ CSV</button>
                  <button onClick={saveExtractedPdfText} className="rounded-lg bg-emerald-600 px-3 py-2 text-sm text-white">حفظ النص TXT</button>
                </>
              )}
              <button onClick={() => setLocalPreview(null)} className="rounded-lg bg-white/10 px-4 py-2 text-white">إغلاق</button>
            </div>
          </div>
          {localPreview.kind === 'csv' || localPreview.kind === 'xlsx' ? (
            <>
              {localPreview.kind === 'xlsx' && (
                <div className="flex flex-wrap items-center gap-3 pb-3">
                  <button onClick={exportEditedXlsx} className="rounded-lg bg-blue-500 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-400">
                    حفظ نسخة Excel جديدة
                  </button>
                  <p className="text-xs text-gray-300">يمكنك تعديل خلايا الورقة الأولى. يحاول الحفظ تحديث الخلايا التي غيّرتها داخل نسخة من المصنف الأصلي مع إبقاء بقية الأوراق والبيانات؛ قد لا يحافظ على كل خصائص Excel المتقدمة.</p>
                </div>
              )}
              {localPreview.kind === 'csv' && (
                <div className="flex flex-wrap items-center gap-3 pb-3">
                  <button onClick={exportCsvToXlsx} className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-500">
                    تحويل CSV إلى Excel وحفظه
                  </button>
                  <p className="text-xs text-gray-300">يُصدّر جميع الصفوف إلى ورقة Excel واحدة. يفيد هذا كمسار وسيط للجداول التي صُدّرت من Access إلى CSV.</p>
                </div>
              )}
              <div className="flex-1 overflow-auto rounded-xl bg-white text-gray-900">
                <table className="min-w-full border-collapse text-sm">
                  <tbody>
                    {localPreview.rows.slice(0, 500).map((row, ri) => (
                      <tr key={ri} className={ri === 0 ? 'bg-gray-100 font-bold' : 'odd:bg-white even:bg-gray-50'}>
                        {row.map((cell, ci) => (
                          <td key={ci} className="border border-gray-200 p-0 align-top">
                            {localPreview.kind === 'xlsx' ? (
                              <input aria-label={`الصف ${ri + 1} العمود ${ci + 1}`} value={cell ?? ''} onChange={e => updatePreviewCell(ri, ci, e.target.value)} className="min-w-24 w-full bg-transparent px-3 py-2 text-gray-900 outline-none focus:bg-blue-50 focus:ring-2 focus:ring-inset focus:ring-blue-400" />
                            ) : (
                              <div className="px-3 py-2 whitespace-pre-wrap">{cell}</div>
                            )}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
                {localPreview.rows.length > 500 && <p className="p-3 text-xs text-gray-500">المعاينة تعرض أول 500 صف فقط؛ التصدير يشمل جميع الصفوف المقروءة.</p>}
                {localPreview.rows.length === 0 && <p className="p-4">الملف فارغ أو لا يحتوي صفوفًا قابلة للعرض.</p>}
              </div>
            </>
          ) : localPreview.kind === 'access' ? (
            <div className="flex-1 overflow-auto rounded-xl bg-white p-5 text-gray-900" dir="auto">
              <h3 className="text-lg font-bold mb-3">معلومات قاعدة بيانات Microsoft Access</h3>
              <p className="mb-2">الصيغة: <strong>{localPreview.accessInfo?.format}</strong></p>
              <p className="mb-2">الحجم: <strong>{((localPreview.accessInfo?.size || 0) / 1024).toFixed(1)} كيلوبايت</strong></p>
              <p className="mb-2">ترويسة الملف: <code>{localPreview.accessInfo?.signature}</code></p>
              <p className="mb-4">فحص الترويسة: <strong>{localPreview.accessInfo?.recognized ? 'تطابق مبدئي' : 'لم يتم تأكيد الصيغة'}</strong></p>
              <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm leading-7">
                هذه المرحلة تفحص معلومات الملف محليًا فقط. لا يتضمن المشروع حتى الآن محركًا يقرأ جداول MDB/ACCDB أو يستخرجها، لذلك لم تُعرض سجلات قاعدة البيانات ولم يُحوّل محتواها.
              </div>
              <button onClick={exportAccessCopy} className="mt-4 rounded-lg bg-blue-600 px-4 py-3 font-semibold text-white">حفظ نسخة من ملف Access</button>
              <p className="mt-2 text-xs text-gray-500">ينشئ نسخة مطابقة من الملف دون تعديل الأصل؛ لا يحوّل الجداول إلى Excel.</p>
            </div>
          ) : localPreview.kind === 'docx' ? (
            <>
              <div className="flex flex-wrap items-center gap-3 pb-3">
                <button onClick={exportEditedDocx} className="rounded-lg bg-blue-500 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-400">
                  حفظ نسخة Word جديدة
                </button>
                <p className="text-xs text-gray-300">يمكنك تعديل الفقرات وخلايا الجداول أدناه. سيتم إنشاء DOCX جديد؛ لن يتغير الملف الأصلي.</p>
              </div>
              <div className="flex-1 overflow-auto rounded-xl bg-white p-4 text-gray-900 text-left" dir="auto">
                {(localPreview.blocks || []).map((block, i) => block.type === 'table' ? (
                  <div key={i} className="overflow-auto my-4"><table className="min-w-full border-collapse text-sm"><tbody>{(block.rows || []).map((row, ri) => <tr key={ri}>{row.map((cell, ci) => <td key={ci} className="border border-gray-300 p-0"><textarea aria-label={`جدول ${i + 1}، الصف ${ri + 1}، العمود ${ci + 1}`} value={cell} onChange={e => updateDocxCell(i, ri, ci, e.target.value)} rows={Math.max(1, Math.min(5, cell.split('\n').length))} className="min-w-24 w-full resize-y bg-transparent px-3 py-2 text-gray-900 outline-none focus:bg-blue-50" /></td>)}</tr>)}</tbody></table></div>
                ) : <textarea key={i} aria-label={`الفقرة ${i + 1}`} value={block.text || ''} onChange={e => updateDocxParagraph(i, e.target.value)} rows={Math.max(2, Math.min(8, (block.text || '').split('\n').length + 1))} className="mb-3 block w-full resize-y rounded border border-gray-200 p-3 text-gray-900 whitespace-pre-wrap focus:border-blue-400 focus:outline-none" />)}
                {(localPreview.blocks || []).length === 0 && <p>لم يتم العثور على نص قابل للعرض.</p>}
              </div>
            </>
          ) : (
            <pre className="flex-1 overflow-auto rounded-xl bg-white p-4 text-sm text-gray-900 whitespace-pre-wrap break-words">{localPreview.text}</pre>
          )}
        </div>
      )}

      {previewFile?.previewUrl && (
        <div className="fixed inset-0 z-50 bg-black/90 p-3 md:p-8 flex flex-col" role="dialog" aria-modal="true" aria-label="معاينة PDF">
          <div className="flex items-center justify-between gap-3 pb-3">
            <div className="min-w-0">
              <h2 className="text-white font-semibold truncate">{previewFile.file.name}</h2>
              <p className="text-xs text-gray-400">معاينة PDF في المتصفح. داخل أندرويد يُفتح الملف عبر عارض PDF المثبت على الهاتف.</p>
            </div>
            <button onClick={() => setPreviewFile(null)} className="rounded-lg bg-white/10 px-4 py-2 text-white">إغلاق</button>
          </div>
          <PdfCanvasViewer file={previewFile.file} />
        </div>
      )}

      <footer className="mt-20 border-t border-white/5 py-12">
        <div className="max-w-7xl mx-auto px-8 flex flex-col md:flex-row justify-between items-center gap-8">
          <div className="flex flex-wrap justify-center gap-8 text-[9px] uppercase tracking-[0.3em] text-[#555] font-black">
            <span>docmo • نسخة تطوير أولية</span>
            <span className="text-blue-500/50">معاينة PDF محلية</span>
            <span>التحويل يحتاج خادمًا</span>
          </div>
          <div className="flex gap-4 items-center">
            <div className="text-right">
              <div className="text-[11px] font-black uppercase tracking-widest text-white">docmo</div>
              <div className="text-[10px] text-[#555] uppercase tracking-tighter">نسخة قيد التطوير</div>
            </div>
            <div className="w-12 h-12 rounded-full glass flex items-center justify-center font-serif italic text-xl border-blue-500/20 text-blue-400 shadow-[0_0_20px_rgba(59,130,246,0.1)]">
              L
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
