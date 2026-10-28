import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;
import 'package:flutter_pdfview/flutter_pdfview.dart';
import 'package:excel/excel.dart' as excel_lib;
import 'package:share_plus/share_plus.dart';

/// عارض المستندات الذكي والاحترافي لتطبيق "كاتب"
/// يدعم صيغ: PDF, Word/TXT (.docx, .doc, .txt), Excel/CSV (.xlsx, .xls, .csv)
/// مع الثيم الملكي: الأزرق الداكن (#0F172A) والألوان الذهبية (#D97706)
class AdvancedDocumentViewerPage extends StatefulWidget {
  final String filePath;
  final String fileName;

  const AdvancedDocumentViewerPage({
    Key? key,
    required this.filePath,
    required this.fileName,
  }) : super(key: key);

  @override
  State<AdvancedDocumentViewerPage> createState() => _AdvancedDocumentViewerPageState();
}

class _AdvancedDocumentViewerPageState extends State<AdvancedDocumentViewerPage> {
  // الثيم الملكي لتطبيق كاتب
  static const Color royalNavyDark = Color(0xFF0F172A);
  static const Color royalNavyCard = Color(0xFF1E293B);
  static const Color royalGold = Color(0xFFD97706);
  static const Color royalGoldLight = Color(0xFFFBBF24);

  bool _showAppBar = true;
  bool _isBookmarked = false;
  double _textSize = 16.0;

  @override
  Widget build(BuildContext context) {
    final String extension = path.extension(widget.filePath).toLowerCase();

    return Directionality(
      textDirection: TextDirection.rtl, // دعم كامل للغة العربية واتجاه RTL
      child: Scaffold(
        backgroundColor: royalNavyDark,
        appBar: _showAppBar
            ? AppBar(
                backgroundColor: royalNavyDark,
                elevation: 0,
                iconTheme: const IconThemeData(color: royalGold),
                leading: IconButton(
                  icon: const Icon(Icons.arrow_back_ios_new, color: royalGold, size: 20),
                  onPressed: () => Navigator.of(context).maybePop(),
                  tooltip: "رجوع",
                ),
                title: Text(
                  widget.fileName,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
                actions: [
                  if (extension == '.txt' || extension == '.docx' || extension == '.doc') ...[
                    IconButton(
                      icon: const Icon(Icons.zoom_in, color: royalGold),
                      tooltip: "تكبير الخط",
                      onPressed: () => setState(() => _textSize = (_textSize + 2).clamp(12.0, 32.0)),
                    ),
                    IconButton(
                      icon: const Icon(Icons.zoom_out, color: royalGold),
                      tooltip: "تصغير الخط",
                      onPressed: () => setState(() => _textSize = (_textSize - 2).clamp(12.0, 32.0)),
                    ),
                  ],
                  IconButton(
                    icon: Icon(
                      _isBookmarked ? Icons.bookmark : Icons.bookmark_border,
                      color: royalGold,
                    ),
                    tooltip: "علامة مرجعية",
                    onPressed: () {
                      setState(() => _isBookmarked = !_isBookmarked);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            _isBookmarked ? "تمت إضافة العلامة المرجعية ⭐" : "تمت إزالة العلامة المرجعية",
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                          ),
                          duration: const Duration(seconds: 1),
                          backgroundColor: royalNavyCard,
                          behavior: SnackBarBehavior.floating,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                      );
                    },
                  ),
                  IconButton(
                    icon: const Icon(Icons.share, color: Colors.white70),
                    tooltip: "مشاركة المستند",
                    onPressed: () {
                      if (widget.filePath.isNotEmpty) {
                        Share.shareXFiles([XFile(widget.filePath)], text: widget.fileName);
                      }
                    },
                  ),
                ],
              )
            : null,
        body: GestureDetector(
          onTap: () => setState(() => _showAppBar = !_showAppBar),
          child: SafeArea(child: _buildViewerBody(extension)),
        ),
      ),
    );
  }

  Widget _buildViewerBody(String ext) {
    final file = File(widget.filePath);
    if (!file.existsSync() && widget.filePath.isNotEmpty) {
      return _buildErrorWidget("عذراً، لم يتم العثور على الملف في المسار المحدد.");
    }

    switch (ext) {
      case '.pdf':
        return _buildPdfViewer();
      case '.txt':
      case '.docx':
      case '.doc':
        return _buildTextViewer(file);
      case '.xlsx':
      case '.xls':
      case '.csv':
        return _buildExcelViewer(file);
      default:
        return _buildErrorWidget("عذراً، صيغة الملف غير مدعومة حالياً.");
    }
  }

  /// 1. عارض ملفات الـ PDF عالي الأداء
  Widget _buildPdfViewer() {
    return PDFView(
      filePath: widget.filePath,
      enableSwipe: true,
      swipeHorizontal: false,
      autoSpacing: true,
      pageFling: true,
      backgroundColor: royalNavyDark,
      onError: (error) {
        debugPrint("PDFView Error: $error");
      },
      onPageError: (page, error) {
        debugPrint("PDFView Page $page Error: $error");
      },
    );
  }

  /// 2. عارض النصوص ومستندات Word
  Widget _buildTextViewer(File file) {
    return FutureBuilder<String>(
      future: file.readAsString(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator(color: royalGold));
        }
        if (snapshot.hasError) {
          return _buildErrorWidget("تعذر قراءة محتوى المستند النصي.");
        }
        return SingleChildScrollView(
          padding: const EdgeInsets.all(20.0),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: royalNavyCard,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: royalGold.withOpacity(0.3)),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.3),
                  blurRadius: 8,
                  offset: const Offset(0, 3),
                ),
              ],
            ),
            child: SelectableText(
              snapshot.data ?? "",
              style: TextStyle(
                color: Colors.white,
                fontSize: _textSize,
                height: 1.8,
                fontFamily: 'Amiri',
              ),
            ),
          ),
        );
      },
    );
  }

  /// 3. عارض ملفات Excel وجداول البيانات
  Widget _buildExcelViewer(File file) {
    return FutureBuilder<List<List<dynamic>>>(
      future: _parseExcelFile(file),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator(color: royalGold));
        }
        if (snapshot.hasError || !snapshot.hasData || snapshot.data!.isEmpty) {
          return _buildErrorWidget("تعذر تحليل بيانات جدول البيانات.");
        }

        final rows = snapshot.data!;
        return SingleChildScrollView(
          scrollDirection: Axis.vertical,
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: DataTable(
                headingRowColor: WidgetStateProperty.all(royalNavyCard),
                dataRowColor: WidgetStateProperty.resolveWith<Color>((states) {
                  if (states.contains(WidgetState.hovered)) {
                    return royalGold.withOpacity(0.12);
                  }
                  return royalNavyDark;
                }),
                border: TableBorder.all(color: royalGold.withOpacity(0.3), width: 1),
                columns: List.generate(
                  rows.first.length,
                  (index) => DataColumn(
                    label: Text(
                      rows.first[index]?.toString() ?? '',
                      style: const TextStyle(
                        color: royalGoldLight,
                        fontWeight: FontWeight.bold,
                        fontSize: 13,
                      ),
                    ),
                  ),
                ),
                rows: rows.skip(1).map((row) {
                  return DataRow(
                    cells: row.map((cell) {
                      return DataCell(
                        Text(
                          cell?.toString() ?? '',
                          style: const TextStyle(color: Colors.white, fontSize: 12),
                        ),
                      );
                    }).toList(),
                  );
                }).toList(),
              ),
            ),
          ),
        );
      },
    );
  }

  /// استخراج صفوف الجدول من ملف الـ Excel عبر مكتبة excel
  Future<List<List<dynamic>>> _parseExcelFile(File file) async {
    final bytes = await file.readAsBytes();
    final excel = excel_lib.Excel.decodeBytes(bytes);
    final List<List<dynamic>> tableData = [];

    for (var table in excel.tables.keys) {
      for (var row in excel.tables[table]!.rows) {
        tableData.add(row.map((cell) => cell?.value).toList());
      }
      break; // قراءة أول ورقة عمل رئيسية
    }
    return tableData;
  }

  Widget _buildErrorWidget(String message) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: royalNavyCard,
                shape: BoxShape.circle,
                border: Border.all(color: royalGold, width: 2),
              ),
              child: const Icon(Icons.warning_amber_rounded, size: 54, color: royalGold),
            ),
            const SizedBox(height: 20),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white70, fontSize: 16),
            ),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(
                backgroundColor: royalGold,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              ),
              onPressed: () => Navigator.of(context).maybePop(),
              icon: const Icon(Icons.arrow_back),
              label: const Text("العودة للقائمة"),
            ),
          ],
        ),
      ),
    );
  }
}
