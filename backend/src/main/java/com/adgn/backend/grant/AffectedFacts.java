package com.adgn.backend.grant;

import java.util.List;

public record AffectedFacts(List<SourceChange> sources,List<RelationChange> relations) {
    public record SourceChange(GrantReader.Source before,GrantReader.Source after) { }
    public record RelationChange(GrantReader.Key key,String sourceId,String baselineId,long beforeVersion,long afterVersion,String grantId,String status) { }
    public AffectedFacts { sources=List.copyOf(sources);relations=List.copyOf(relations); }
}
