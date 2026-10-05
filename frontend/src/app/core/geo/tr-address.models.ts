/** `assets/geo/iller.json` satırı */
export interface TrIl {
  sehir_id: string;
  sehir_adi: string;
}

/** `assets/geo/ilceler.json` satırı */
export interface TrIlce {
  ilce_id: string;
  ilce_adi: string;
  sehir_id: string;
  sehir_adi: string;
}

/** `assets/geo/mahalleler-*.json` satırı */
export interface TrMahalle {
  mahalle_id: string;
  mahalle_adi: string;
  ilce_id: string;
  ilce_adi: string;
  sehir_id: string;
  sehir_adi: string;
}
