import 'package:flutter/material.dart';
import 'advanced_document_viewer.dart';

const _navy = Color(0xFF101B35);
const _navyCard = Color(0xFF1B2947);
const _gold = Color(0xFFD6A84F);
const _goldLight = Color(0xFFF0D18A);

void main() => runApp(const KatibApp());

class KatibApp extends StatefulWidget {
  const KatibApp({super.key});

  @override
  State<KatibApp> createState() => _KatibAppState();
}

class _KatibAppState extends State<KatibApp> {
  ThemeMode _themeMode = ThemeMode.light;

  void _toggleTheme() => setState(() {
        _themeMode =
            _themeMode == ThemeMode.light ? ThemeMode.dark : ThemeMode.light;
      });

  ThemeData _theme(bool dark) {
    final scheme = ColorScheme.fromSeed(
      seedColor: _gold,
      brightness: dark ? Brightness.dark : Brightness.light,
      primary: dark ? _goldLight : _navy,
      secondary: _gold,
      surface: dark ? _navyCard : Colors.white,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: dark ? _navy : const Color(0xFFF5F6F9),
      appBarTheme: AppBarTheme(
        backgroundColor: dark ? _navy : const Color(0xFFF5F6F9),
        foregroundColor: dark ? Colors.white : _navy,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardTheme(
        color: dark ? _navyCard : Colors.white,
        elevation: dark ? 0 : 1,
        shadowColor: Colors.black.withValues(alpha: .05),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        margin: EdgeInsets.zero,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: dark ? _navyCard : Colors.white,
        indicatorColor: _gold.withValues(alpha: .18),
        labelTextStyle: WidgetStateProperty.resolveWith((states) =>
            TextStyle(
              fontSize: 11,
              fontWeight: states.contains(WidgetState.selected)
                  ? FontWeight.w700
                  : FontWeight.w500,
            )),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: dark ? _navyCard : Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Katib',
      debugShowCheckedModeBanner: false,
      theme: _theme(false),
      darkTheme: _theme(true),
      themeMode: _themeMode,
      home: KatibHomeScreen(
        isDark: _themeMode == ThemeMode.dark,
        onToggleTheme: _toggleTheme,
      ),
    );
  }
}

class KatibHomeScreen extends StatefulWidget {
  final bool isDark;
  final VoidCallback onToggleTheme;

  const KatibHomeScreen({
    super.key,
    required this.isDark,
    required this.onToggleTheme,
  });

  @override
  State<KatibHomeScreen> createState() => _KatibHomeScreenState();
}

class _KatibHomeScreenState extends State<KatibHomeScreen> {
  int _selectedIndex = 0;
  final TextEditingController _searchController = TextEditingController();

  final List<Map<String, String>> _documents = const [
    {'name': 'دليل المستخدم - Katib.pdf', 'path': '/storage/emulated/0/Download/دليل_المستخدم_كاتب.pdf', 'type': 'PDF'},
    {'name': 'تقرير الإنتاجية الأسبوعي.xlsx', 'path': '/storage/emulated/0/Download/تقرير_الإنتاجية_الأسبوعي.xlsx', 'type': 'Excel'},
    {'name': 'مسودة رواية تاريخية.docx', 'path': '/storage/emulated/0/Download/مسودة_رواية_تاريخية.docx', 'type': 'Word'},
    {'name': 'ملاحظات البحث.txt', 'path': '/storage/emulated/0/Download/ملاحظات_البحث.txt', 'type': 'Text'},
  ];

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      _dashboard(),
      _library(),
      _tools(),
      _settings(),
    ];
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        appBar: AppBar(
          title: Row(
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: _gold,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.edit_document, color: _navy, size: 22),
              ),
              const SizedBox(width: 11),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Katib', style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: .3)),
                  Text('مساحتك للكتابة والإنتاج', style: TextStyle(fontSize: 10, color: Theme.of(context).hintColor)),
                ],
              ),
            ],
          ),
          actions: [
            IconButton(
              tooltip: widget.isDark ? 'المظهر الفاتح' : 'المظهر الداكن',
              onPressed: widget.onToggleTheme,
              icon: Icon(widget.isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined),
            ),
            const SizedBox(width: 6),
          ],
        ),
        body: SafeArea(child: AnimatedSwitcher(
          duration: const Duration(milliseconds: 220),
          child: pages[_selectedIndex],
        )),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _selectedIndex,
          onDestinationSelected: (value) => setState(() => _selectedIndex = value),
          destinations: const [
            NavigationDestination(icon: Icon(Icons.grid_view_rounded), label: 'الرئيسية'),
            NavigationDestination(icon: Icon(Icons.folder_open_rounded), label: 'مكتبتي'),
            NavigationDestination(icon: Icon(Icons.auto_awesome_outlined), label: 'الأدوات'),
            NavigationDestination(icon: Icon(Icons.tune_rounded), label: 'الإعدادات'),
          ],
        ),
      ),
    );
  }

  Widget _dashboard() {
    final isDark = widget.isDark;
    return ListView(
      key: const ValueKey('dashboard'),
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 28),
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              begin: Alignment.topRight,
              end: Alignment.bottomLeft,
              colors: [_navy, Color(0xFF26395E)],
            ),
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: _gold.withValues(alpha: .42)),
          ),
          child: Stack(
            children: [
              Positioned(left: -8, bottom: -24, child: Icon(Icons.auto_stories, size: 116, color: Colors.white.withValues(alpha: .06))),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    const Icon(Icons.waving_hand_outlined, color: _goldLight, size: 18),
                    const SizedBox(width: 8),
                    Text('مرحباً بك في Katib', style: TextStyle(color: Colors.white.withValues(alpha: .78), fontSize: 13)),
                  ]),
                  const SizedBox(height: 14),
                  const Text('أفكارك تستحق\nأن تُكتب بإتقان.', style: TextStyle(color: Colors.white, fontSize: 25, height: 1.35, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 9),
                  Text('اكتب، نظّم، واقرأ مستنداتك في مساحة واحدة.', style: TextStyle(color: Colors.white.withValues(alpha: .72), fontSize: 12)),
                  const SizedBox(height: 20),
                  FilledButton.icon(
                    style: FilledButton.styleFrom(backgroundColor: _gold, foregroundColor: _navy, padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 13)),
                    onPressed: () => _showCreateDialog(),
                    icon: const Icon(Icons.add, size: 19),
                    label: const Text('مستند جديد', style: TextStyle(fontWeight: FontWeight.w800)),
                  ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        TextField(
          controller: _searchController,
          onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(
            hintText: 'ابحث عن مستند أو أداة...',
            prefixIcon: Icon(Icons.search_rounded),
            suffixIcon: Icon(Icons.tune_rounded, size: 19),
          ),
        ),
        const SizedBox(height: 24),
        _sectionTitle('الوصول السريع', 'كل ما تحتاجه بخطوة'),
        const SizedBox(height: 13),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.65,
          children: [
            _quickCard(Icons.edit_note_rounded, 'الكتابة والتحرير', 'ابدأ مستنداً', () => _showCreateDialog()),
            _quickCard(Icons.picture_as_pdf_rounded, 'قارئ PDF', 'عرض ملفات PDF', () => setState(() => _selectedIndex = 1)),
            _quickCard(Icons.table_chart_rounded, 'الجداول', 'ملفات Excel', () => setState(() => _selectedIndex = 1)),
            _quickCard(Icons.folder_copy_outlined, 'إدارة الملفات', 'تصفح المكتبة', () => setState(() => _selectedIndex = 1)),
          ],
        ),
        const SizedBox(height: 25),
        _sectionTitle('المستندات الأخيرة', 'تابع عملك من حيث توقفت'),
        const SizedBox(height: 12),
        ..._filteredDocuments.take(3).map(_documentTile),
        if (_filteredDocuments.isEmpty)
          const Padding(padding: EdgeInsets.all(22), child: Center(child: Text('لا توجد نتائج مطابقة'))),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(onPressed: () => setState(() => _selectedIndex = 1), child: const Text('عرض المكتبة كاملة')),
        ),
        const SizedBox(height: 12),
        Row(children: [
          Icon(Icons.offline_bolt_outlined, color: _gold, size: 18),
          const SizedBox(width: 8),
          Text('مصمم للعمل محلياً مع ملفاتك', style: TextStyle(color: Theme.of(context).hintColor, fontSize: 11)),
        ]),
      ],
    );
  }

  List<Map<String, String>> get _filteredDocuments {
    final q = _searchController.text.trim().toLowerCase();
    if (q.isEmpty) return _documents;
    return _documents.where((d) => d['name']!.toLowerCase().contains(q) || d['type']!.toLowerCase().contains(q)).toList();
  }

  Widget _library() => ListView(
    key: const ValueKey('library'),
    padding: const EdgeInsets.all(20),
    children: [
      _pageHeading('مكتبتي', 'مستنداتك وملفاتك في مكان واحد'),
      const SizedBox(height: 18),
      TextField(controller: _searchController, onChanged: (_) => setState(() {}), decoration: const InputDecoration(hintText: 'ابحث في المستندات...', prefixIcon: Icon(Icons.search))),
      const SizedBox(height: 18),
      ..._filteredDocuments.map(_documentTile),
      const SizedBox(height: 14),
      Text('ملاحظة: ستظهر هنا الملفات التي تضيفها إلى مكتبتك.', style: TextStyle(color: Theme.of(context).hintColor, fontSize: 12)),
    ],
  );

  Widget _tools() => ListView(
    key: const ValueKey('tools'),
    padding: const EdgeInsets.all(20),
    children: [
      _pageHeading('الأدوات', 'أدوات تساعدك على إنجاز أعمالك'),
      const SizedBox(height: 18),
      _toolTile(Icons.picture_as_pdf_outlined, 'قارئ PDF', 'فتح وقراءة ملفات PDF', () => setState(() => _selectedIndex = 1)),
      _toolTile(Icons.table_chart_outlined, 'عارض الجداول', 'استعراض ملفات Excel وCSV', () => setState(() => _selectedIndex = 1)),
      _toolTile(Icons.text_snippet_outlined, 'قارئ النصوص', 'قراءة ملفات TXT', () => setState(() => _selectedIndex = 1)),
      _toolTile(Icons.share_outlined, 'مشاركة المستندات', 'مشاركة الملفات المدعومة', () => setState(() => _selectedIndex = 1)),
    ],
  );

  Widget _settings() => ListView(
    key: const ValueKey('settings'),
    padding: const EdgeInsets.all(20),
    children: [
      _pageHeading('الإعدادات', 'خصص تجربة Katib كما تحب'),
      const SizedBox(height: 18),
      Card(child: SwitchListTile(
        secondary: const Icon(Icons.dark_mode_outlined, color: _gold),
        title: const Text('المظهر الداكن'),
        subtitle: const Text('التبديل بين المظهر الفاتح والداكن'),
        value: widget.isDark,
        onChanged: (_) => widget.onToggleTheme(),
      )),
      Card(child: ListTile(
        leading: const Icon(Icons.language_rounded, color: _gold),
        title: const Text('لغة التطبيق'),
        subtitle: const Text('العربية - اتجاه من اليمين إلى اليسار'),
      )),
      Card(child: ListTile(
        leading: const Icon(Icons.info_outline_rounded, color: _gold),
        title: const Text('حول Katib'),
        subtitle: const Text('مساحة متكاملة لقراءة وتأليف المستندات'),
      )),
    ],
  );

  Widget _pageHeading(String title, String subtitle) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(title, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
      const SizedBox(height: 5),
      Text(subtitle, style: TextStyle(color: Theme.of(context).hintColor, fontSize: 12)),
    ],
  );

  Widget _sectionTitle(String title, String subtitle) => Row(
    crossAxisAlignment: CrossAxisAlignment.end,
    children: [
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
        const SizedBox(height: 3),
        Text(subtitle, style: TextStyle(fontSize: 11, color: Theme.of(context).hintColor)),
      ])),
      Container(width: 34, height: 3, decoration: BoxDecoration(color: _gold, borderRadius: BorderRadius.circular(8))),
    ],
  );

  Widget _quickCard(IconData icon, String title, String subtitle, VoidCallback onTap) => Card(
    child: InkWell(
      borderRadius: BorderRadius.circular(20),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(children: [
          Container(width: 42, height: 42, decoration: BoxDecoration(color: _gold.withValues(alpha: .15), borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: _gold, size: 22)),
          const SizedBox(width: 10),
          Expanded(child: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 12)),
            const SizedBox(height: 4),
            Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 10, color: Theme.of(context).hintColor)),
          ])),
        ]),
      ),
    ),
  );

  Widget _documentTile(Map<String, String> doc) {
    final type = doc['type']!;
    final icon = type == 'PDF' ? Icons.picture_as_pdf_rounded : type == 'Excel' ? Icons.table_chart_rounded : Icons.description_outlined;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Card(
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 5),
          leading: Container(width: 44, height: 44, decoration: BoxDecoration(color: _gold.withValues(alpha: .14), borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: _gold)),
          title: Text(doc['name']!, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
          subtitle: Text('$type • ملف محلي', style: TextStyle(fontSize: 10, color: Theme.of(context).hintColor)),
          trailing: const Icon(Icons.chevron_left_rounded),
          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => AdvancedDocumentViewerPage(filePath: doc['path']!, fileName: doc['name']!))),
        ),
      ),
    );
  }

  Widget _toolTile(IconData icon, String title, String subtitle, VoidCallback onTap) => Card(
    child: ListTile(
      leading: Container(width: 44, height: 44, decoration: BoxDecoration(color: _gold.withValues(alpha: .14), borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: _gold)),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 11)),
      trailing: const Icon(Icons.chevron_left_rounded),
      onTap: onTap,
    ),
  );

  void _showCreateDialog() {
    final controller = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('إنشاء مستند'),
        content: TextField(controller: controller, autofocus: true, decoration: const InputDecoration(hintText: 'اسم المستند')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: const Text('إلغاء')),
          FilledButton(onPressed: () {
            Navigator.pop(dialogContext);
            ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('واجهة إنشاء المستند قيد الربط بمحرر الكتابة.')));
          }, child: const Text('متابعة')),
        ],
      ),
    );
  }
}
