package com.daetaguham.store.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.daetaguham.store.domain.StoreNotice;

public record StoreNoticeResponse(
		Long id,
		Long storeId,
		String storeName,
		LocalDate noticeDate,
		String content,
		String createdByName,
		LocalDateTime createdAt
) {
	public static StoreNoticeResponse from(StoreNotice notice) {
		return new StoreNoticeResponse(
				notice.getId(),
				notice.getStore().getId(),
				notice.getStore().getName(),
				notice.getNoticeDate(),
				notice.getContent(),
				notice.getCreatedBy().getName(),
				notice.getCreatedAt()
		);
	}
}
