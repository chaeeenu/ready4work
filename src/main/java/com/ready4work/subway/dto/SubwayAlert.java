package com.ready4work.subway.dto;

public record SubwayAlert(
        String noftTtl,
        String noftCn,
        String noftOcrnDt,
        String lineNmLst,
        String stnSctnCdLst,
        String xcseSitnBgngDt,
        String xcseSitnEndDt,
        String nonstopYn
) {}
