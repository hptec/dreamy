package com.dreamy.controller;

import com.dreamy.domain.guide.service.UserGuideTaskService;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/store/account/guide-progress")
public class StoreGuideProgressController {
    private final UserGuideTaskService service;
    public StoreGuideProgressController(UserGuideTaskService service) { this.service = service; }
    @GetMapping public ResponseEntity<R<Map<Long, List<Long>>>> list() { return ResponseEntity.ok(R.ok(service.list(userId()))); }
    @PutMapping("/{guideId}") public ResponseEntity<R<Map<Long, List<Long>>>> replace(@PathVariable Long guideId, @RequestBody ProgressRequest request) { return ResponseEntity.ok(R.ok(service.replace(userId(), guideId, request == null ? null : request.taskIds()))); }
    private Long userId() { AuthPrincipal p = AuthContext.get(); if (p == null || !AuthPrincipal.TYPE_STORE.equals(p.type())) throw new BizException(ErrorCode.UNAUTHORIZED); return Long.parseLong(p.subject()); }
    public record ProgressRequest(List<Long> taskIds) {}
}
