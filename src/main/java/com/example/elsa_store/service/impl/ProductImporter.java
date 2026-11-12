package com.example.elsa_store.service.impl;

import com.example.elsa_store.dto.response.ProductImportResponse;
import com.example.elsa_store.entity.Category;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.repository.CategoryRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.repository.ProductVariantRepository;
import com.example.elsa_store.security.AccessGuard;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Nhập sản phẩm hàng loạt từ Excel (chủ / quản lý). Mỗi dòng là một phân loại; các dòng cùng tên sản phẩm gộp thành một
 * sản phẩm (thông tin sản phẩm lấy ở dòng đầu). Tên trùng sản phẩm đã có của cửa hàng thì thêm phân loại vào sản phẩm đó.
 * Kiểm tra hết mọi dòng trước: có một lỗi là không ghi gì, trả danh sách lỗi theo số dòng.
 */
@Service
@Transactional
public class ProductImporter {

    static final int MAX_ROWS = 500;
    static final String[] HEADERS = {
        "Tên sản phẩm *", "Danh mục *", "Giá gốc *", "Mô tả", "Màu", "Size", "SKU", "Giá phân loại", "Tồn kho",
        "Giá vốn", "Trạng thái"
    };
    private static final int NAME = 0, CATEGORY = 1, BASE_PRICE = 2, DESCRIPTION = 3, COLOR = 4, SIZE = 5, SKU = 6,
            PRICE = 7, QUANTITY = 8, COST = 9, STATUS = 10;

    private final AccessGuard accessGuard;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final StockService stockService;
    private final NewProductAlerts newProductAlerts;

    public ProductImporter(AccessGuard accessGuard,
                           CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           ProductVariantRepository variantRepository,
                           StockService stockService,
                           NewProductAlerts newProductAlerts) {
        this.accessGuard = accessGuard;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.stockService = stockService;
        this.newProductAlerts = newProductAlerts;
    }

    /** Một dòng đã đọc và kiểm tra. {@code basePrice} null ở dòng thêm phân loại cho sản phẩm đã biết; {@code price}
     * null = lấy giá gốc của sản phẩm. */
    record Line(int row, String name, Category category, Double basePrice, String description, String color,
                String size, String sku, Double price, Integer quantity, Double cost, int status) {}

    public ProductImportResponse importFile(MultipartFile file, boolean dryRun) {
        Store store = accessGuard.requireStoreManager();
        User actor = accessGuard.currentUser();
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "file: chọn tệp Excel (.xlsx)");
        }
        List<List<String>> rows;
        try (InputStream in = file.getInputStream()) {
            rows = read(in);
        } catch (IOException | RuntimeException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "file: không đọc được tệp Excel (.xlsx)");
        }
        return importRows(rows, store, actor, dryRun);
    }

    /** {@code rows}: các dòng dữ liệu (bỏ dòng tiêu đề), mỗi ô đã đổi sang chuỗi. Dòng i ở Excel là i + 2. */
    ProductImportResponse importRows(List<List<String>> rows, Store store, User actor, boolean dryRun) {
        List<ProductImportResponse.Issue> errors = new ArrayList<>();
        if (rows.size() > MAX_ROWS) {
            errors.add(new ProductImportResponse.Issue(1, "Tối đa " + MAX_ROWS + " dòng mỗi lần nhập"));
            return result(false, rows.size(), 0, 0, 0, 0, errors);
        }
        Map<String, Category> categories = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            categories.put(key(c.getName()), c);
            categories.put(String.valueOf(c.getId()), c);
        }
        Map<String, Product> existing = new HashMap<>();
        for (Product p : productRepository.findAllByStore_Id(store.getId())) {
            existing.putIfAbsent(key(p.getName()), p);
        }

        List<Line> lines = new ArrayList<>();
        Map<String, Line> firstOfProduct = new HashMap<>();
        Set<String> skus = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            int row = i + 2;
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            List<String> problems = new ArrayList<>();
            String name = cell(cells, NAME);
            if (name.isEmpty()) {
                problems.add("thiếu tên sản phẩm");
            } else if (name.length() > 255) {
                problems.add("tên sản phẩm quá 255 ký tự");
            }
            Category category = categories.get(key(cell(cells, CATEGORY)));
            if (cell(cells, CATEGORY).isEmpty()) {
                problems.add("thiếu danh mục");
            } else if (category == null) {
                problems.add("không có danh mục \"" + cell(cells, CATEGORY) + "\"");
            }
            // Giá gốc chỉ cần ở dòng tạo sản phẩm mới; dòng thêm phân loại cho sản phẩm đã có / đã gặp thì bỏ qua cột này.
            boolean known = !name.isEmpty() && (existing.containsKey(key(name)) || firstOfProduct.containsKey(key(name)));
            Double basePrice = money(cell(cells, BASE_PRICE), "giá gốc", !known, problems);
            Double price = money(cell(cells, PRICE), "giá phân loại", false, problems);
            Double cost = money(cell(cells, COST), "giá vốn", false, problems);
            Integer quantity = count(cell(cells, QUANTITY), problems);
            Integer status = status(cell(cells, STATUS), problems);
            String color = limited(cell(cells, COLOR), 50, "màu", problems);
            String size = limited(cell(cells, SIZE), 50, "size", problems);
            String sku = limited(cell(cells, SKU), 50, "SKU", problems);
            if (!sku.isEmpty()) {
                if (!skus.add(sku.toLowerCase(Locale.ROOT))) {
                    problems.add("SKU " + sku + " bị lặp trong tệp");
                } else if (variantRepository.existsBySku(sku)) {
                    problems.add("SKU " + sku + " đã có");
                }
            }
            Line first = name.isEmpty() ? null : firstOfProduct.get(key(name));
            if (first != null && category != null && first.category() != null
                    && !first.category().getId().equals(category.getId())) {
                problems.add("khác danh mục với dòng " + first.row() + " cùng sản phẩm");
            }
            if (!problems.isEmpty()) {
                errors.add(new ProductImportResponse.Issue(row, String.join("; ", problems)));
                continue;
            }
            Line line = new Line(row, name, category, basePrice, emptyToNull(cell(cells, DESCRIPTION)), emptyToNull(color),
                    emptyToNull(size), emptyToNull(sku), price, quantity, cost,
                    status == null ? 1 : status);
            firstOfProduct.putIfAbsent(key(name), line);
            lines.add(line);
        }
        if (lines.isEmpty() && errors.isEmpty()) {
            errors.add(new ProductImportResponse.Issue(2, "Tệp chưa có dòng dữ liệu nào (bắt đầu từ dòng 2)"));
        }

        // Đếm trước để xem trước và lúc có lỗi vẫn biết tệp sẽ tạo bao nhiêu.
        Set<String> newProducts = new LinkedHashSet<>();
        Set<String> extended = new LinkedHashSet<>();
        int tracked = 0;
        for (Line line : lines) {
            (existing.containsKey(key(line.name())) ? extended : newProducts).add(key(line.name()));
            if (line.quantity() != null) {
                tracked++;
            }
        }
        if (!errors.isEmpty() || dryRun) {
            return result(false, rows.size(), newProducts.size(), extended.size(), lines.size(), tracked, errors);
        }

        Map<String, Product> products = new HashMap<>(existing);
        List<Product> created = new ArrayList<>();
        for (Line line : lines) {
            Product product = products.get(key(line.name()));
            if (product == null) {
                product = productRepository.save(Product.builder()
                        .name(line.name())
                        .description(line.description())
                        .basePrice(line.basePrice())
                        .category(line.category())
                        .status(line.status())
                        .store(store)
                        .build());
                products.put(key(line.name()), product);
                created.add(product);
            }
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setColor(line.color());
            variant.setSize(line.size());
            variant.setSku(line.sku());
            variant.setPrice(line.price() != null ? line.price() : product.getBasePrice());
            variant.setStatus(1);
            variant.setCostPrice(line.cost());
            variant = variantRepository.save(variant);
            if (line.quantity() != null) {
                stockService.adjust(variant, line.quantity(), null, "Nhập từ Excel", actor);
            }
        }
        newProductAlerts.afterImport(store, created);
        return result(true, rows.size(), newProducts.size(), extended.size(), lines.size(), tracked, List.of());
    }

    /** Tệp mẫu: sheet "Sản phẩm" (tiêu đề + ví dụ) và sheet "Hướng dẫn" (ý nghĩa từng cột, danh mục đang có). */
    @Transactional(readOnly = true)
    public byte[] template() {
        accessGuard.requireStoreManager();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);

            Sheet sheet = workbook.createSheet("Sản phẩm");
            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(bold);
            }
            List<Category> categories = categoryRepository.findAll();
            String sampleCategory = categories.isEmpty() ? "Giày" : categories.getFirst().getName();
            Object[][] samples = {
                {"Giày chạy bộ Elsa Run", sampleCategory, 890000, "Đế êm, thoáng khí", "Đen", "40", "RUN-DEN-40", null, 10, 500000, "Đang bán"},
                {"Giày chạy bộ Elsa Run", sampleCategory, 890000, null, "Đen", "41", "RUN-DEN-41", null, 8, 500000, "Đang bán"},
                {"Giày chạy bộ Elsa Run", sampleCategory, 890000, null, "Trắng", "41", "RUN-TRANG-41", 920000, null, null, "Đang bán"},
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    Object value = samples[r][c];
                    if (value instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            for (int c = 0; c < HEADERS.length; c++) {
                sheet.setColumnWidth(c, 18 * 256);
            }

            Sheet guide = workbook.createSheet("Hướng dẫn");
            String[][] notes = {
                {"Cột", "Ý nghĩa"},
                {HEADERS[NAME], "Bắt buộc. Các dòng cùng tên gộp thành một sản phẩm; trùng tên sản phẩm đã có thì thêm phân loại vào sản phẩm đó."},
                {HEADERS[CATEGORY], "Bắt buộc. Tên danh mục (không phân biệt hoa thường) hoặc mã số danh mục — xem danh sách bên dưới."},
                {HEADERS[BASE_PRICE], "Bắt buộc ở dòng đầu của sản phẩm mới, số đồng (ví dụ 890000). Dòng thêm phân loại cho sản phẩm đã có thì bỏ trống được."},
                {HEADERS[DESCRIPTION], "Tuỳ chọn, lấy ở dòng đầu của mỗi sản phẩm."},
                {HEADERS[COLOR] + " / " + HEADERS[SIZE], "Tuỳ chọn, tối đa 50 ký tự."},
                {HEADERS[SKU], "Tuỳ chọn, không trùng trong tệp và với mã đã có."},
                {HEADERS[PRICE], "Tuỳ chọn; trống = bằng giá gốc của sản phẩm."},
                {HEADERS[QUANTITY], "Tuỳ chọn; có số thì bắt đầu theo dõi kho với số này, trống = bán không giới hạn."},
                {HEADERS[COST], "Tuỳ chọn, để tính lợi nhuận."},
                {HEADERS[STATUS], "\"Đang bán\" (mặc định) hoặc \"Ẩn\"."},
                {"", ""},
                {"Danh mục đang có", ""},
            };
            int r = 0;
            for (String[] note : notes) {
                Row row = guide.createRow(r++);
                row.createCell(0).setCellValue(note[0]);
                row.createCell(1).setCellValue(note[1]);
                if (r == 1) {
                    row.getCell(0).setCellStyle(bold);
                    row.getCell(1).setCellStyle(bold);
                }
            }
            for (Category c : categories) {
                Row row = guide.createRow(r++);
                row.createCell(0).setCellValue(c.getId());
                row.createCell(1).setCellValue(c.getParent() == null ? c.getName() : c.getParent().getName() + " › " + c.getName());
            }
            guide.setColumnWidth(0, 22 * 256);
            guide.setColumnWidth(1, 90 * 256);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build import template", e);
        }
    }

    /** Sheet đầu tiên, bỏ dòng tiêu đề; mỗi ô đổi sang chuỗi (số nguyên không kèm ".0"). */
    static List<List<String>> read(InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<List<String>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < HEADERS.length; c++) {
                    Cell cell = row == null ? null : row.getCell(c);
                    String value;
                    if (cell == null) {
                        value = "";
                    } else if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                        double d = cell.getNumericCellValue();
                        value = d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
                    } else {
                        value = formatter.formatCellValue(cell, evaluator);
                    }
                    cells.add(value.trim());
                }
                rows.add(cells);
            }
            return rows;
        }
    }

    private static ProductImportResponse result(boolean applied, int rows, int created, int extended, int variants,
                                                int tracked, List<ProductImportResponse.Issue> errors) {
        return new ProductImportResponse(applied, rows, created, extended, variants, tracked, errors);
    }

    private static String cell(List<String> cells, int index) {
        return index < cells.size() && cells.get(index) != null ? cells.get(index).trim() : "";
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String limited(String value, int max, String label, List<String> problems) {
        if (value.length() > max) {
            problems.add(label + " quá " + max + " ký tự");
        }
        return value;
    }

    /** "890000", "890.000", "890,000", "890000đ" → 890000; trống thì null (báo lỗi nếu bắt buộc). */
    static Double money(String raw, String label, boolean required, List<String> problems) {
        if (raw.isEmpty()) {
            if (required) {
                problems.add("thiếu " + label);
            }
            return null;
        }
        String digits = raw.replaceAll("(?i)[\\s.,đd₫vnd]", "");
        if (raw.matches(".*\\d[.,]\\d{1,2}$") && !raw.matches(".*\\d[.,]\\d{3}$")) {
            digits = raw.replaceAll("[\\s]", "").replace(',', '.');
        }
        try {
            double value = Double.parseDouble(digits);
            if (value < 0 || value > 1_000_000_000) {
                problems.add(label + " phải từ 0 đến 1.000.000.000");
                return null;
            }
            if (required && value <= 0) {
                problems.add(label + " phải lớn hơn 0");
                return null;
            }
            return (double) Math.round(value);
        } catch (NumberFormatException e) {
            problems.add(label + " \"" + raw + "\" không phải số");
            return null;
        }
    }

    private static Integer count(String raw, List<String> problems) {
        if (raw.isEmpty()) {
            return null;
        }
        // "1.000" / "1,000" là nghìn; "1,5" là số lẻ → báo lỗi chứ không đọc thành 15.
        String compact = raw.replaceAll("\\s", "");
        if (!compact.matches("\\d+|\\d{1,3}([.,]\\d{3})+")) {
            problems.add("tồn kho \"" + raw + "\" không phải số nguyên");
            return null;
        }
        try {
            int value = Integer.parseInt(compact.replaceAll("[.,]", ""));
            if (value < 0 || value > 1_000_000) {
                problems.add("tồn kho phải từ 0 đến 1.000.000");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            problems.add("tồn kho \"" + raw + "\" không phải số nguyên");
            return null;
        }
    }

    private static Integer status(String raw, List<String> problems) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "", "đang bán", "dang ban", "bán", "1" -> 1;
            case "ẩn", "an", "0" -> 0;
            default -> {
                problems.add("trạng thái \"" + raw + "\" phải là Đang bán hoặc Ẩn");
                yield null;
            }
        };
    }
}
