import 'package:flutter/material.dart';
import 'advanced_document_viewer.dart';

void main() {
  runApp(const KatebApp());
}

class KatebApp extends StatelessWidget {
  const KatebApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'كاتب',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        fontFamily: 'Amiri',
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFD97706),
          secondary: Color(0xFFFBBF24),
          surface: Color(0xFF1E293B),
        ),
      ),
      home: const KatebHomeScreen(),
    );
  }
}

class KatebHomeScreen extends StatelessWidget {
  const KatebHomeScreen({Key? key}) : super(key: key);

  // قائمة ملفات تجريبية لتوضيح الربط المباشر
  final List<Map<String, String>> sampleDocs = const [
    {"name": "دليل_المستخدم_كاتب.pdf", "path": "/storage/emulated/0/Download/دليل_المستخدم_كاتب.pdf", "type": "PDF"},
    {"name": "تقرير_الإنتاجية_الأسبوعي.xlsx", "path": "/storage/emulated/0/Download/تقرير_الإنتاجية_الأسبوعي.xlsx", "type": "Excel"},
    {"name": "مسودة_رواية_تاريخية.docx", "path": "/storage/emulated/0/Download/مسودة_رواية_تاريخية.docx", "type": "Word"},
    {"name": "ملاحظات_البحث.txt", "path": "/storage/emulated/0/Download/ملاحظات_البحث.txt", "type": "Text"},
  ];

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        appBar: AppBar(
          backgroundColor: const Color(0xFF0F172A),
          title: const Text(
            "تطبيق كاتب • المستندات والمكتبة",
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 17),
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.folder_open, color: Color(0xFFD97706)),
              onPressed: () {
                // فتح منتقي الملفات
              },
            ),
          ],
        ),
        body: ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: sampleDocs.length,
          separatorBuilder: (context, index) => const SizedBox(height: 12),
          itemBuilder: (context, index) {
            final doc = sampleDocs[index];
            return Container(
              decoration: BoxDecoration(
                color: const Color(0xFF1E293B),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFD97706).withOpacity(0.2)),
              ),
              child: ListTile(
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: const Color(0xFFD97706).withOpacity(0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(
                    doc["type"] == "PDF"
                        ? Icons.picture_as_pdf
                        : doc["type"] == "Excel"
                            ? Icons.table_chart
                            : Icons.article,
                    color: const Color(0xFFD97706),
                  ),
                ),
                title: Text(
                  doc["name"]!,
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14),
                ),
                subtitle: Text(
                  "صيغة ${doc["type"]} • محلي بدون إنترنت",
                  style: const TextStyle(color: Colors.white54, fontSize: 11),
                ),
                trailing: const Icon(Icons.arrow_forward_ios, color: Color(0xFFD97706), size: 14),
                onTap: () {
                  // فتح عارض المستندات الاحترافي مباشرة
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => AdvancedDocumentViewerPage(
                        filePath: doc["path"]!,
                        fileName: doc["name"]!,
                      ),
                    ),
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }
}
