package com.kitapsepeti.catalog.exception;

import com.kitapsepeti.common.error.ApiException;

/** Kategori kendisinin veya alt ağacındaki bir kategorinin altına taşınamaz (409). */
public class CategoryCycleException extends ApiException {

	public CategoryCycleException() {
		super(CatalogErrorCode.CATEGORY_CYCLE);
	}

}
