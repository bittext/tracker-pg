package com.svp.tracker.finance.controller;

import com.svp.tracker.finance.dto.FinanceTax1040ReturnDto;
import com.svp.tracker.finance.service.FinanceTax1040Service;
import com.svp.tracker.finance.service.FinanceTax1040Service.Tax1040FileContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/finance/tax/1040")
@RequiredArgsConstructor
public class FinanceTax1040Controller {

    private final FinanceTax1040Service service;

    @GetMapping("/returns")
    public List<FinanceTax1040ReturnDto> list(
            @RequestParam(name = "fullText", required = false, defaultValue = "false") boolean fullText) {
        return service.list(fullText);
    }

    @GetMapping("/returns/{id}")
    public FinanceTax1040ReturnDto get(
            @PathVariable long id,
            @RequestParam(name = "fullText", required = false, defaultValue = "true") boolean fullText) {
        return service.get(id, fullText);
    }

    @PostMapping(value = "/returns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceTax1040ReturnDto upload(
            @RequestParam("taxYear") int taxYear, @RequestParam("file") MultipartFile file) throws IOException {
        return service.uploadOrReplace(taxYear, file);
    }

    @DeleteMapping("/returns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    @GetMapping("/returns/{id}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable long id, @RequestParam(defaultValue = "attachment") String disposition) {
        Tax1040FileContent f = service.readFile(id);
        String mode = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }
}
