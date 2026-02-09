package com.kade.AIAssistant.feature.conversation.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractOption;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@NoArgsConstructor
public class DocumentService {

    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);

        try {
            if (lower.endsWith(".hwp")) {
                return extractFromHwp(file);
            }

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.read();
            String merged = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            log.info("문서 텍스트 추출 완료(Tika): name={}, size={}, length={}",
                    filename, file.getSize(), merged.length());
            return merged;
        } catch (IOException e) {
            throw new RuntimeException("파일 읽기 실패: " + e.getMessage(), e);
        }
    }

    // hwp 확장자 별도 라이브러리로 처리
    private String extractFromHwp(MultipartFile file) {
        Path temp = null;
        try {
            temp = Files.createTempFile("upload-", ".hwp");
            Files.write(temp, file.getBytes());

            HWPFile hwp = HWPReader.fromFile(temp.toString());
            TextExtractOption option = new TextExtractOption();
            option.setWithControlChar(false);
            String text = TextExtractor.extract(hwp, option);

            log.info("문서 텍스트 추출 완료(HWP): name={}, size={}, length={}",
                    file.getOriginalFilename(), file.getSize(), text.length());
            return text;
        } catch (Exception e) {
            throw new RuntimeException("HWP 파싱 실패: " + e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignore) {
                }
            }
        }
    }
}
