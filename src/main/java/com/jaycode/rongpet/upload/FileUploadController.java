package com.jaycode.rongpet.upload;

import com.jaycode.rongpet.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 小程序图片上传入口。单次一张，客户端压缩后上传；返回对象存储 key 与可访问 URL。
 */
@Tag(name = "App-文件上传")
@RestController
@RequestMapping("/app/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "上传图片", description = "jpg/jpeg/png，单张不超过5MB；category 用于归档，如 post/claim/feeder")
    @PostMapping("/upload")
    public R<UploadVO> upload(@RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) String category) {
        return R.ok(fileStorageService.upload(file, category));
    }
}
