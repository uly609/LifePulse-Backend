package com.lifepulse.agent;

import java.util.List;

public record OpsDiagnosis(
        String riskLevel,
        String summary,
        List<String> findings,
        List<String> suggestions,
        List<String> evidence
) {
}
