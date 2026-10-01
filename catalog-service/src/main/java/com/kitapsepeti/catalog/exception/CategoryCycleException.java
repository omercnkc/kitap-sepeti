package com.kitapsepeti.catalog.exception;

/** Kategori kendisinin veya alt ağacındaki bir kategorinin altına taşınamaz (409). */
public class CategoryCycleException extends ApiException {

	public CategoryCycleException() {
		super(ErrorCode.CATEGORY_CYCLE);
	}

}
