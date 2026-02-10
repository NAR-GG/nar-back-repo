package com.toy.nar.app.record.dto;

import java.util.List;
import java.util.Map;

public record FearlessDto(
                Map<String, List<String>> blue,
                Map<String, List<String>> red) {
}
