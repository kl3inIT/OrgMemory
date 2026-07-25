package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetReviewDecisionRepository extends JpaRepository<AssetReviewDecision, UUID> {

    List<AssetReviewDecision> findByReviewCaseIdOrderByDecidedAtAsc(UUID reviewCaseId);
}
