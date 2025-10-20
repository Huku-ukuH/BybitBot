package org.example.update;

import org.example.monitor.dto.PositionInfo;

import java.util.List;

public record UpdateResult(
        String log,
        List<String> updated,
        List<String> closed,
        List<PositionInfo> newPositions
) {}