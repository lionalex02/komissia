import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.logging.*;

public class DirectorySyncAppSimple {

    static class ComparisonInfo {
        String relativePath;
        String status;
        Path pathInA;
        Path pathInB;
        int displayIndex = 0;

        ComparisonInfo(String relativePath, String status, Path pathInA, Path pathInB) {
            this.relativePath = relativePath;
            this.status = status;
            this.pathInA = pathInA;
            this.pathInB = pathInB;
        }

        String getRelativePath() {
            return relativePath;
        }
    }


    private Path pathA = Path.of("C:\\Users\\liona\\Desktop\\Комиссия\\komissia\\src\\pathA");
    private Path pathB = Path.of("C:\\Users\\liona\\Desktop\\Комиссия\\komissia\\src\\pathB");
    private Path pathC = Path.of("C:\\Users\\liona\\Desktop\\Комиссия\\komissia\\src\\pathC");
    private String logFilePath = "LOGS.log";
    private Logger logger;
    private Scanner consoleScanner;

    private Map<String, Path> lastScanMapA = new HashMap<>();
    private Map<String, Path> lastScanMapB = new HashMap<>();
    private List<ComparisonInfo> lastComparisonResult = new ArrayList<>();
    private int lastNumberedItemCount = 0;


    public static void main(String[] args) {
        DirectorySyncAppSimple app = new DirectorySyncAppSimple();
        app.consoleScanner = new Scanner(System.in);
        app.parseArguments(args);

        try {
            app.setupLogger();
        } catch (IOException e) {
            System.err.println("КРИТИЧЕСКАЯ ОШИБКА: Не удалось создать файл лога! " + e.getMessage());
            app.consoleScanner.close();
            return;
        }

        app.runMainMenu();

        app.closeLogger();
        app.consoleScanner.close();
        System.out.println("Приложение завершило работу.");
    }

    void parseArguments(String[] args) {
        if (args.length >= 1) {
            pathA = Paths.get(args[0]);
        }
        if (args.length >= 2) {
            pathB = Paths.get(args[1]);
        }
        if (args.length >= 3 && !args[2].startsWith("--")) {
            pathC = Paths.get(args[2]);
        }
        for (String arg : args) {
            if (arg.startsWith("--log=")) {
                logFilePath = arg.substring("--log=".length());
            }
        }
    }

    void setupLogger() throws IOException {
        logger = Logger.getLogger("SimpleSync");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        SimpleFormatter formatter = new SimpleFormatter();

        try {
            Path logPath = Paths.get(logFilePath);
            if (logPath.getParent() != null) {
                Files.createDirectories(logPath.getParent());
            }
            FileHandler fileHandler = new FileHandler(logFilePath, true);
            fileHandler.setFormatter(formatter);
            fileHandler.setLevel(Level.INFO);
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            System.err.println("Внимание: Не удалось настроить запись лога в файл: " + e.getMessage());
            throw e;
        }

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(formatter);
        logger.addHandler(consoleHandler);

        logger.info("Логгер запущен. Файл лога: " + Paths.get(logFilePath).toAbsolutePath());
    }

    void closeLogger() {
        if (logger != null) {
            for (Handler handler : logger.getHandlers()) {
                handler.close();
            }
        }
    }

    void runMainMenu() {
        ensurePathsSelected(pathA);
        ensurePathsSelected(pathB);

        int choice;
        do {
            displayMainMenu();
            System.out.print("Выберите действие: ");
            try {
                choice = Integer.parseInt(consoleScanner.nextLine());
            } catch (NumberFormatException e) {
                choice = -1;
            }

            switch (choice) {
                case 1:
                    analyzeAndDisplay(true, false);
                    break;
                case 2:
                    analyzeAndDisplay(false, true);
                    break;
                case 3:
                    analyzeAndDisplay(true, true);
                    break;
                case 4:
                    runSyncMenu();
                    break;
                case 5:
                    runChangePathsMenu();
                    break;
                case 6:
                    logger.info("Выход из программы.");
                    break;
                default:
                    System.out.println("Неверный выбор. Пожалуйста, введите число от 1 до 6.");
                    logger.warning("Введен неверный пункт меню.");
            }
            if (choice != 6) {
                System.out.println("\nНажмите Enter для возврата в меню...");
                consoleScanner.nextLine();
            }
        } while (choice != 6);
    }

    void ensurePathsSelected(Path path) {
        while (path == null || !Files.isDirectory(path)) {
            System.out.print("Введите ПОЛНЫЙ путь к Папке: ");
            String input = consoleScanner.nextLine().trim();
            if (!input.isEmpty()) {
                path = Paths.get(input);
                if (!Files.isDirectory(path)) {
                    System.out.println("Ошибка: Указанный путь не является действительным каталогом.");
                    path = null;
                }
            }
        }
    }

    void displayMainMenu() {
        System.out.println("\n===== ГЛАВНОЕ МЕНЮ =====");
        System.out.println("Выбрано:");
        System.out.println("  Папка А: " + (pathA != null ? pathA.toAbsolutePath() : "НЕ ЗАДАНО"));
        System.out.println("  Папка B: " + (pathB != null ? pathB.toAbsolutePath() : "НЕ ЗАДАНО"));
        System.out.println("  Папка C (для слияния): " + (pathC != null ? pathC.toAbsolutePath() : "НЕ ЗАДАНО"));
        System.out.println("  Файл лога: " + Paths.get(logFilePath).toAbsolutePath());
        System.out.println("Действия:");
        System.out.println("  1. Анализ измененных/новых файлов");
        System.out.println("  2. Анализ идентичных файлов");
        System.out.println("  3. Полный анализ");
        System.out.println("  4. Синхронизация");
        System.out.println("  5. Сменить каталоги / путь к лог-файлу");
        System.out.println("  6. Выход");
        System.out.println("==========================");
    }

    void analyzeAndDisplay(boolean showModifiedNew, boolean showIdentical) {
        if (pathA == null || pathB == null) {
            System.out.println("Ошибка: Сначала выберите Папку А и Папку B.");
            return;
        }
        logger.info("Запуск анализа...");
        lastScanMapA = scanDirectory(pathA);
        lastScanMapB = scanDirectory(pathB);
        compareLists();
        assignDisplayIndexes();

        System.out.println("\n===== РЕЗУЛЬТАТЫ АНАЛИЗА =====");
        displayTree("Содержимое Папки А (" + pathA.toAbsolutePath() + ")", true, showModifiedNew, showIdentical);
        displayTree("Содержимое Папки B (" + pathB.toAbsolutePath() + ")", false, showModifiedNew, showIdentical);

        System.out.println("===============================");
        logger.info("Отображение результатов анализа завершено.");
    }

    Map<String, Path> scanDirectory(Path rootDir) {
        Map<String, Path> filesMap = new HashMap<>();
        logger.info("Сканирование каталога: " + rootDir);
        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        String relativePath = rootDir.relativize(file).toString().replace(File.separatorChar, '/');
                        filesMap.put(relativePath, file);
                    } catch (Exception e) {
                        logger.warning("Не удалось обработать файл: " + file + " Ошибка: " + e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    logger.warning("Ошибка доступа к файлу/каталогу: " + file + " Ошибка: " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.severe("Ошибка сканирования каталога " + rootDir + ": " + e.getMessage());
        }
        logger.info("Найдено " + filesMap.size() + " файлов в " + rootDir);
        return filesMap;
    }


    void compareLists() {
        lastComparisonResult.clear();
        Set<String> allRelativePaths = new HashSet<>();
        allRelativePaths.addAll(lastScanMapA.keySet());
        allRelativePaths.addAll(lastScanMapB.keySet());

        for (String relativePath : allRelativePaths) {
            Path pathInA = lastScanMapA.get(relativePath);
            Path pathInB = lastScanMapB.get(relativePath);
            String status = "ОШИБКА";

            if (pathInA != null && pathInB != null) {
                try {
                    long sizeA = Files.size(pathInA);
                    long sizeB = Files.size(pathInB);
                    Instant modA = Files.getLastModifiedTime(pathInA).toInstant();
                    Instant modB = Files.getLastModifiedTime(pathInB).toInstant();
                    if (sizeA == sizeB && modA.equals(modB)) {
                        status = "ИДЕНТИЧНЫЙ";
                    } else {
                        if (modA.isAfter(modB)) {
                            status = "ИЗМЕНЕН (А новее)";
                        } else if (modB.isAfter(modA)) {
                            status = "ИЗМЕНЕН (B новее)";
                        }
                    }
                } catch (IOException e) {
                    logger.warning("Не удалось сравнить файлы: " + relativePath + " Ошибка: " + e.getMessage());
                    status = "ОШИБКА (чтение)";
                }
            } else if (pathInA != null) {
                status = "НОВЫЙ (в А)";
            } else if (pathInB != null) {
                status = "НОВЫЙ (в B)";
            }
            lastComparisonResult.add(new ComparisonInfo(relativePath, status, pathInA, pathInB));
        }

        lastComparisonResult.sort(Comparator.comparing(ComparisonInfo::getRelativePath));

        logger.info("Сравнение завершено. Всего уникальных относительных путей: " + lastComparisonResult.size());
    }

    void assignDisplayIndexes() {
        int totalItemsInList = lastComparisonResult.size();
        lastNumberedItemCount = 0;

        for (int i = 0; i < totalItemsInList; i++) {
            ComparisonInfo info = lastComparisonResult.get(i);
            info.displayIndex = i + 1;
        }
        lastNumberedItemCount = totalItemsInList;

        logger.info("Присвоены номера для выбора " + lastNumberedItemCount + " элементам.");
    }


    void displayTree(String title, boolean isForA, boolean showModNew, boolean showIdentical) {
        System.out.println("\n--- " + title + " ---");
        boolean itemsDisplayed = false;
        Set<String> printedDirs = new HashSet<>();

        for (ComparisonInfo info : lastComparisonResult) {
            boolean shouldDisplayByFilter = false;
            if (showModNew && (info.status.startsWith("НОВЫЙ") || info.status.startsWith("ИЗМЕНЕН"))) {
                shouldDisplayByFilter = true;
            }
            if (showIdentical && info.status.equals("ИДЕНТИЧНЫЙ")) {
                shouldDisplayByFilter = true;
            }
            if (!shouldDisplayByFilter) {
                continue;
            }

            boolean existsInCurrentTree = (isForA && info.pathInA != null) || (!isForA && info.pathInB != null);
            boolean displayThisLine = existsInCurrentTree || info.status.startsWith("ИЗМЕНЕН") || info.status.equals("ИДЕНТИЧНЫЙ");

            if (!displayThisLine) {
                continue;
            }

            String relativePath = info.relativePath;
            Path pathObject = Paths.get(relativePath);
            String fileName = pathObject.getFileName().toString();
            Path parentPathObject = pathObject.getParent();
            int depth = (parentPathObject == null) ? 0 : parentPathObject.getNameCount();

            String currentPathPrefix = "";
            if (parentPathObject != null) {
                for (int i = 0; i < depth; i++) {
                    currentPathPrefix += (i > 0 ? File.separator : "") + parentPathObject.getName(i).toString();
                    if (!printedDirs.contains(currentPathPrefix)) {
                        String dirIndent = "  ".repeat(i);
                        System.out.println(dirIndent + "+ " + parentPathObject.getName(i).toString());
                        printedDirs.add(currentPathPrefix);
                    }
                }
            }

            String fileIndent = "  ".repeat(depth);
            String displayStatus = info.status;
            if (isForA && info.status.equals("ИЗМЕНЕН (B новее)")) {
                displayStatus = "УСТАРЕЛ (B новее)";
            } else if (!isForA && info.status.equals("ИЗМЕНЕН (А новее)")) {
                displayStatus = "УСТАРЕЛ (А новее)";
            }
            String indexString = (info.displayIndex > 0) ? " (№" + info.displayIndex + ")" : "";

            System.out.println(fileIndent + "- " + fileName + " [" + displayStatus + indexString + "]");
            itemsDisplayed = true;

        }
        if (!itemsDisplayed) {
            System.out.println("(Нет файлов для отображения с этим фильтром)");
        }
    }


    void runSyncMenu() {
        analyzeAndDisplay(true, true);

        System.out.println("\n===== МЕНЮ СИНХРОНИЗАЦИИ =====");
        System.out.println("1. Синхронизировать A -> B (Копировать новое/измененное из A в B)");
        System.out.println("2. Синхронизировать B -> A (Копировать новое/измененное из B в A)");
        System.out.println("3. Слияние A + B -> C (Копировать уникальное/новейшее в C)");
        System.out.println("4. Выборочная синхронизация A -> B");
        System.out.println("5. Выборочная синхронизация B -> A");
        System.out.println("6. Выборочное слияние A + B -> C");
        System.out.println("7. Назад");
        System.out.println("==============================");
        System.out.print("Выберите действие: ");

        int choice;
        try {
            choice = Integer.parseInt(consoleScanner.nextLine());
        } catch (NumberFormatException e) {
            choice = -1;
        }

        String targetType = null;
        boolean isSelective = false;
        boolean forceOverwrite = false;

        switch (choice) {
            case 1: targetType = "A_TO_B"; break;
            case 2: targetType = "B_TO_A"; break;
            case 3: targetType = "MERGE_TO_C"; break;
            case 4: targetType = "A_TO_B"; isSelective = true; break;
            case 5: targetType = "B_TO_A"; isSelective = true; break;
            case 6: targetType = "MERGE_TO_C"; isSelective = true; break;
            case 7: return;
            default: System.out.println("Неверный выбор."); return;
        }

        if (targetType.equals("MERGE_TO_C")) {
            if (pathC == null) {
                System.out.print("Введите ПОЛНЫЙ путь к Папке C для слияния: ");
                String input = consoleScanner.nextLine().trim();
                if (!input.isEmpty()) {
                    pathC = Paths.get(input);
                    if (!Files.isDirectory(pathC)) {
                        try {
                            Files.createDirectories(pathC);
                            logger.info("Создан каталог для Папки С: " + pathC);
                        } catch (IOException e) {
                            System.out.println("Ошибка создания каталога C: " + e.getMessage());
                            logger.severe("Не удалось создать каталог C: " + pathC);
                            pathC = null;
                            return;
                        }
                    }
                } else {
                    System.out.println("Слияние отменено. Требуется путь к Папке C.");
                    return;
                }
            }
            if (pathC.equals(pathA) || pathC.equals(pathB)) {
                System.out.println("Ошибка: Папка C не может совпадать с Папкой А или B.");
                pathC = null;
                return;
            }
        }

        Set<Integer> selectedIndices = new HashSet<>();
        if (isSelective) {
            System.out.print("Введите номера файлов для синхронизации (например: 1, 3-5, 8), макс. #" + lastNumberedItemCount + ": ");
            String input = consoleScanner.nextLine();
            selectedIndices = parseSelection(input, lastNumberedItemCount);
            if (selectedIndices.isEmpty()) {
                System.out.println("Не выбрано ни одного действительного файла. Синхронизация отменена.");
                return;
            }
            System.out.println("Выбранные элементы (по номерам): " + selectedIndices);
        }
        synchronizeFiles(targetType, selectedIndices, forceOverwrite);
    }

    Set<Integer> parseSelection(String input, int maxIndex) {
        Set<Integer> selected = new HashSet<>();
        if (input == null || input.trim().isEmpty()) return selected;

        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();
            try {
                if (part.contains("-")) {
                    String[] range = part.split("-", 2);
                    int start = Integer.parseInt(range[0].trim());
                    int end = Integer.parseInt(range[1].trim());
                    if (start >= 1 && end <= maxIndex && start <= end) {
                        for (int i = start; i <= end; i++) {
                            selected.add(i);
                        }
                    } else {
                        logger.warning("Неверный диапазон в выборе: " + part);
                    }
                } else {
                    int index = Integer.parseInt(part);
                    if (index >= 1 && index <= maxIndex) {
                        selected.add(index);
                    } else {
                        logger.warning("Неверный индекс в выборе: " + part);
                    }
                }
            } catch (NumberFormatException e) {
                logger.warning("Неверный формат числа в выборе: " + part);
            }
        }
        return selected;
    }


    void synchronizeFiles(String targetType, Set<Integer> selectedIndices, boolean forceOverwrite) {
        logger.info("Запуск синхронизации: " + targetType +
                (selectedIndices.isEmpty() ? " (Все подходящие)" : " (Выбрано: " + selectedIndices.size() + ")") +
                (forceOverwrite ? " [ПРИНУДИТЕЛЬНО]" : ""));

        int successCount = 0;
        int errorCount = 0;

        for (ComparisonInfo info : lastComparisonResult) {
            if (info.displayIndex <= 0 || (!selectedIndices.isEmpty() && !selectedIndices.contains(info.displayIndex))) {
                continue;
            }

            String relativePath = info.relativePath;
            String status = info.status;

            Path sourcePath = null;
            Path targetPath = null;
            String reason = "";
            boolean shouldCopy = false;

            switch (targetType) {
                case "A_TO_B":
                    if (status.equals("НОВЫЙ (в А)") || status.equals("ИЗМЕНЕН (А новее)") || status.equals("ИЗМЕНЕН (размер)")) {
                        sourcePath = info.pathInA;
                        targetPath = pathB.resolve(relativePath);
                        shouldCopy = true;
                        reason = status.equals("НОВЫЙ (в А)") ? "НОВЫЙ" : "ИЗМЕНЕН";
                    } else if (forceOverwrite && status.equals("ИЗМЕНЕН (B новее)")) {
                        sourcePath = info.pathInA;
                        targetPath = pathB.resolve(relativePath);
                        shouldCopy = true;
                        reason = "ИЗМЕНЕН (Принудительно старый из А)";
                    }
                    break;

                case "B_TO_A":
                    if (status.equals("НОВЫЙ (в B)") || status.equals("ИЗМЕНЕН (B новее)") || status.equals("ИЗМЕНЕН (размер)")) {
                        sourcePath = info.pathInB;
                        targetPath = pathA.resolve(relativePath);
                        shouldCopy = true;
                        reason = status.equals("НОВЫЙ (в B)") ? "НОВЫЙ" : "ИЗМЕНЕН";
                    } else if (forceOverwrite && status.equals("ИЗМЕНЕН (А новее)")) {
                        sourcePath = info.pathInB;
                        targetPath = pathA.resolve(relativePath);
                        shouldCopy = true;
                        reason = "ИЗМЕНЕН (Принудительно старый из B)";
                    }
                    break;

                case "MERGE_TO_C":
                    if (pathC == null) { errorCount++; continue; }
                    if (status.equals("НОВЫЙ (в А)")) {
                        sourcePath = info.pathInA;
                        targetPath = pathC.resolve(relativePath);
                        shouldCopy = true;
                        reason = "НОВЫЙ УНИКАЛЬНЫЙ (из A)";
                    } else if (status.equals("НОВЫЙ (в B)")) {
                        sourcePath = info.pathInB;
                        targetPath = pathC.resolve(relativePath);
                        shouldCopy = true;
                        reason = "НОВЫЙ УНИКАЛЬНЫЙ (из B)";
                    } else if (status.equals("ИЗМЕНЕН (А новее)") || status.equals("ИЗМЕНЕН (размер)")) {
                        sourcePath = info.pathInA;
                        targetPath = pathC.resolve(relativePath);
                        shouldCopy = true;
                        reason = "ИЗМЕНЕН (Новейший из А)";
                    } else if (status.equals("ИЗМЕНЕН (B новее)")) {
                        sourcePath = info.pathInB;
                        targetPath = pathC.resolve(relativePath);
                        shouldCopy = true;
                        reason = "ИЗМЕНЕН (Новейший из B)";
                    }
                    break;
            }

            if (shouldCopy && sourcePath != null && targetPath != null) {
                if (copyFile(sourcePath, targetPath)) {
                    successCount++;
                    logger.info("[OK] Скопирован (#" + info.displayIndex + "): " + sourcePath.getFileName() + " (Причина: " + reason + ")");
                } else {
                    errorCount++;
                    logger.warning("[СБОЙ] Ошибка копирования (#" + info.displayIndex + "): " + sourcePath.getFileName());
                }
            }
        }

        System.out.println("\n--- Отчет о синхронизации ---");
        System.out.println("Успешно скопировано: " + successCount);
        System.out.println("Ошибки: " + errorCount);
        System.out.println("-----------------------------");
        logger.info("Синхронизация завершена. Успешно: " + successCount + ", Ошибки: " + errorCount);
    }

    boolean copyFile(Path source, Path target) {
        try {
            Path targetDir = target.getParent();
            if (targetDir != null && !Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            try (InputStream in = new FileInputStream(source.toFile());
                 OutputStream out = new FileOutputStream(target.toFile())) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
            try {
                BasicFileAttributes sourceAttrs = Files.readAttributes(source, BasicFileAttributes.class);
                Files.setLastModifiedTime(target, sourceAttrs.lastModifiedTime());
            } catch (Exception attrError) {
                logger.finer("Не удалось скопировать атрибуты для: " + target + " - " + attrError.getMessage());
            }

            return true;
        } catch (IOException | SecurityException e) {
            logger.severe("Ошибка копирования " + source + " в " + target + ": " + e.getMessage());
            return false;
        }
    }

    void runChangePathsMenu() {
        System.out.println("\n===== Смена Каталогов / Лог-файла =====");
        System.out.println("1. Сменить Папку А (Текущий: " + pathA + ")");
        System.out.println("2. Сменить Папку B (Текущий: " + pathB + ")");
        System.out.println("3. Сменить Папку C (Текущий: " + (pathC != null ? pathC : "Не задана") + ")");
        System.out.println("4. Сменить путь к Лог-файлу (Текущий: " + logFilePath + ")");
        System.out.println("5. Назад");
        System.out.println("========================================");
        System.out.print("Выберите действие: ");

        int choice;
        try {
            choice = Integer.parseInt(consoleScanner.nextLine());
        } catch (NumberFormatException e) {
            choice = -1;
        }

        String newPathStr;
        switch (choice) {
            case 1:
                System.out.print("Новый путь для Папки А: ");
                newPathStr = consoleScanner.nextLine().trim();
                if (!newPathStr.isEmpty()) {
                    Path newP = Paths.get(newPathStr);
                    if (Files.isDirectory(newP) && !newP.equals(pathB) && !newP.equals(pathC)) {
                        pathA = newP;
                        lastComparisonResult.clear();
                        lastNumberedItemCount = 0;
                        logger.info("Путь к Папке А изменен на: " + pathA);
                    } else {
                        System.out.println("Ошибка: Неверный каталог или совпадает с Папкой B/C.");
                    }
                } else {
                    System.out.println("Путь не изменен.");
                }
                break;
            case 2:
                System.out.print("Новый путь для Папки B: ");
                newPathStr = consoleScanner.nextLine().trim();
                if (!newPathStr.isEmpty()) {
                    Path newP = Paths.get(newPathStr);
                    if (Files.isDirectory(newP) && !newP.equals(pathA) && !newP.equals(pathC)) {
                        pathB = newP;
                        lastComparisonResult.clear();
                        lastNumberedItemCount = 0;
                        logger.info("Путь к Папке B изменен на: " + pathB);
                    } else System.out.println("Ошибка: Неверный каталог или совпадает с Папкой A/C.");
                } else System.out.println("Путь не изменен.");
                break;
            case 3:
                System.out.print("Новый путь для Папки C (пусто для сброса): ");
                newPathStr = consoleScanner.nextLine().trim();
                if (!newPathStr.isEmpty()) {
                    Path newP = Paths.get(newPathStr);
                    if (!newP.equals(pathA) && !newP.equals(pathB)) {
                        try {
                            if (!Files.exists(newP)) Files.createDirectories(newP);
                            if (Files.isDirectory(newP)) {
                                pathC = newP; logger.info("Путь к Папке C изменен на: " + pathC);
                            } else {
                                System.out.println("Ошибка: Путь для Папки С не является каталогом.");
                            }
                        } catch (IOException e) {
                            System.out.println("Ошибка создания каталога C: " + e.getMessage());
                        }
                    } else {
                        System.out.println("Ошибка: Папка C не может совпадать с Папкой А или B.");
                    }
                } else {
                    pathC = null; logger.info("Путь к Папке C сброшен."); System.out.println("Путь к Папке C сброшен.");
                }
                break;
            case 4:
                System.out.print("Новый путь для Лог-файла: ");
                newPathStr = consoleScanner.nextLine().trim();
                if (!newPathStr.isEmpty()) {
                    closeLogger();
                    logFilePath = newPathStr;
                    try {
                        setupLogger();
                        System.out.println("Путь к лог-файлу изменен.");
                    } catch (IOException e) {
                        System.err.println("Ошибка установки нового лог-файла: " + e.getMessage());
                        logFilePath = "LOGS.log";
                        try { setupLogger(); } catch (IOException ignored) {}
                        System.out.println("Путь к лог-файлу НЕ изменен. Используется путь по умолчанию.");
                    }
                } else {
                    System.out.println("Путь не изменен.");
                }
                break;
            case 5:
                return;
            default: System.out.println("Неверный выбор.");
        }
        runChangePathsMenu();
    }
}