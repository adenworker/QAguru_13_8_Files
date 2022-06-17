package guru.qa;

import com.codeborne.pdftest.PDF;
import com.codeborne.xlstest.XLS;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestsWithFiles {
    /**Создаем classLoader для чтения папок и файлов из папки resource*/
    ClassLoader classLoader = TestsWithFiles.class.getClassLoader();

    /**В любом методе с чтением или записью добавляем throws Exception, чтобы защитить программу (тест упадет, а
     * тестсьют - нет).
     * Любые виды InputStream при чтении (и OutputStream при записи) оборачиваем в try, чтобы в конце их использования
     * закрыть сокет чтения (или записи).
     */

    /**
     * Архивируем файлы в zip архив и проверяем, что архивация прошла успешно.
     * Файлы для архивации хранятся в resources, а архив будет лежать вне src.
     * Чтобы считывать файлы внутри resources, используем classLoader.getResourceAsStream("archive/zipped.zip").
     * Чтобы считывать папки внутри resources, используем classLoader.getResource("files").
     * Чтобы считывать файлы вне src, используем FileInputStream("test_archives/zipped.zip").
     */
    @Test
    @DisplayName("Запись в архив")
    void zipToArchiveTest() throws IOException {
        //Получаем путь к папка с файлами
        String path = Objects.requireNonNull(classLoader.getResource("files")).getPath();
        //Создаем список с путями к файлам
        File[] files = new File(path).listFiles();
        assert files != null;
        if (files.length == 0) {
            throw new IllegalArgumentException("No files in path " + path);
        }
        //Запускаем процесс записи файла
        try (FileOutputStream fos = new FileOutputStream("test_archives/zipped.zip")) {
            //Запускаем процесс записи архива
            try (ZipOutputStream zipOut = new ZipOutputStream(fos)) {
                //Поочередно архивируем файлы по адресам из списка
                for (File zipThis : files) {
                    //Запускаем процесс считывания файла
                    try (FileInputStream fis = new FileInputStream(zipThis)) {
                        //Даем файлу в архиве соответствующее имя
                        ZipEntry zipEntry = new ZipEntry(zipThis.getName());
                        //Резервируем место для файла внутри архива
                        zipOut.putNextEntry(zipEntry);
                        //Считываем и пишем по 2 килобайта
                        byte[] buffer = new byte[2048];
                        int length;
                        //Процесс записи продолжается, пока в файле есть информация (если ее нет, то .read(bytes) вернет -1
                        while ((length = fis.read(buffer)) >= 0) {
                            zipOut.write(buffer, 0, length);
                        }
                    }
                }
            }
        }
        //Проверяем, что архив существует и читается
        try (InputStream inputArchive = new FileInputStream("test_archives/zipped.zip")) {
            try (ZipInputStream zippedStream = new ZipInputStream(inputArchive)) {
                ZipEntry entry = zippedStream.getNextEntry();
                assert entry != null;
                assertThat(entry.getName().length() > 0).isTrue();
            }
        }
    }

    /**
     * Разархивируем файлы из zip архива, сохраняя их на диск, и проверяем, что в них есть нужная информация.
     * Архив хранится в resources, а файлы будут лежать вне src.
     * Чтобы считывать файлы внутри resources, используем classLoader.getResourceAsStream("archive/zipped.zip").
     * Чтобы считывать папки внутри resources, используем classLoader.getResource("files").
     * Чтобы считывать файлы вне src, используем FileInputStream("test_archives/zipped.zip").
     */
    @Test
    @DisplayName("Чтение файлов архива с сохранением на диск")
    void unzippingTestWithFiles() throws Exception {
        //Запускаем процесс чтения архива
        try (InputStream inputZippedStream = classLoader.getResourceAsStream("archive/zipped.zip")) {
            assert inputZippedStream != null;
            //Запускаем процесс разархивации
            try (ZipInputStream zippedStream = new ZipInputStream(inputZippedStream)) {
                ZipEntry entry;
                //По очереди обрабатываем сущности в архиве
                while ((entry = zippedStream.getNextEntry()) != null) {
                    //Формируем адрес для сохранения файла и его имя
                    File savedFile = new File("test_files/" + entry.getName());
                    //Запускаем процесс записи по указанному адресу
                    try (FileOutputStream outputStream = new FileOutputStream(savedFile)) {
                        //Считываем и пишем по 2 килобайта
                        byte[] buffer = new byte[2048];
                        int length;
                        //Процесс записи продолжается, пока в файле есть информация (если ее нет, то .read(bytes) вернет -1
                        while ((length = zippedStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
            }
        }
        //Проверяем распакованные файлы
        //Сначала CSV
        try (InputStream csvUnpackedInputStream = new FileInputStream("test_files/csv test file.csv")) {
            CSVReader csvUnpackedReader = new CSVReader(new InputStreamReader(csvUnpackedInputStream, UTF_8));
            List<String[]> csvUnpackedList = csvUnpackedReader.readAll();
            assertThat(csvUnpackedList).contains(
                    new String[]{"Jack", "McGinnis", "220 hobo Av.", "Phila", " PA", "09119"}
            );
        }
        //Потом PDF
        try (InputStream pdfUnpackedInputStream = new FileInputStream("test_files/pdf_test_file.pdf")) {
            PDF pdfFile = new PDF(pdfUnpackedInputStream);
            assertThat(pdfFile.text).contains("Please visit our website at:  http://www.education.gov.yk.ca/");
        }
        //И в конце XLSX
        try (InputStream xlsxUnpackedInputStream = new FileInputStream("test_files/xlsx-test-file.xlsx")) {
            XLS xlsxFile = new XLS(xlsxUnpackedInputStream);
            assertThat(xlsxFile.excel.getSheetAt(1).getRow(9).getCell(1)
                    .getStringCellValue()).contains("Judie");
            assertThat(xlsxFile.excel.getSheetAt(1).getRow(9).getCell(2)
                    .getStringCellValue()).contains("Claywell");
        }
    }

    /**
     * Разархивируем файлы из zip архива, без сохранения их на диск, и проверяем, что в них есть нужная информация.
     * Архив хранится в resources.
     * Чтобы считывать файлы внутри resources, используем classLoader.getResourceAsStream("archive/zipped.zip").
     */
    @Test
    @DisplayName("Чтение файлов архива без сохранения на диск")
    void unzippingTestWithoutFiles() throws Exception {
        //Запускаем процесс чтения архива
        try (InputStream inputZippedStream = classLoader.getResourceAsStream("archive/zipped.zip")) {
            assert inputZippedStream != null;
            //Запускаем процесс разархивации
            try (ZipInputStream zippedStream = new ZipInputStream(inputZippedStream)) {
                ZipEntry entry;
                //По очереди обрабатываем сущности в архиве
                while ((entry = zippedStream.getNextEntry()) != null) {
                    //Тут фильтруем по расширению файла и проводим соответствующие проверки
                    if (entry.getName().contains(".csv")) {
                        CSVReader csvFileReader = new CSVReader(new InputStreamReader(zippedStream, UTF_8));
                        List<String[]> csvList = csvFileReader.readAll();
                        assertThat(csvList).contains(
                                new String[] {"Jack", "McGinnis", "220 hobo Av.", "Phila", " PA", "09119"}
                        );
                    } else if (entry.getName().contains(".pdf")) {
                        PDF pdfFile = new PDF(zippedStream);
                        assertThat(pdfFile.text).contains("Please visit our website at:  http://www.education.gov.yk.ca/");
                    } else if (entry.getName().contains(".xlsx")) {
                        XLS xls = new XLS(zippedStream);
                        assertThat(xls.excel.getSheetAt(1).getRow(9).getCell(1)
                                .getStringCellValue()).contains("Judie");
                        assertThat(xls.excel.getSheetAt(1).getRow(9).getCell(2)
                                .getStringCellValue()).contains("Claywell");
                    }
                }
            }
        }
    }

    /**
     * Проверяем json файл с диска на наличие нужной информации
     * ObjectMapper mapper = new ObjectMapper();
     * Map<String, Object> jsonMap = mapper.readValue(inputStream, Map.class);
     */
    @Test
    @DisplayName("Проверка json файла с помощью Jackson")
    void jacksonTest()  throws IOException {
        InputStream is = classLoader.getResourceAsStream("json_test_files/json_test_file.json");
        //Создаем Jackson JsonFactory
        JsonFactory factory = new JsonFactory();
        //Создаем Jackson JsonParser
        JsonParser parser = factory.createParser(is);
        //Создаем Jackson ObjectMapper
        ObjectMapper jacksonMapper = new ObjectMapper();
        //Конвертируем json в map
        Map<String, Map<String, Map<String, Map<String, ArrayList<String>>>>> jsonMap = jacksonMapper
                .readValue(parser, Map.class);
        //Получаем нужный нам для проверки элемент из полученной map
        ArrayList<String> list =  jsonMap.get("quiz").get("maths").get("q1").get("options");
        //Проверяем, что в нужном месте json находится нужная нам информация
        assertThat(list).containsAll(Arrays.asList("10","11","12","13"));
    }
}