import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { map, shareReplay, switchMap } from 'rxjs/operators';
import { TrIl, TrIlce, TrMahalle } from './tr-address.models';

/** `ilce_id` → mahalleler-N parça numaraları */
type MahalleIndex = Record<string, number[]>;

@Injectable({ providedIn: 'root' })
export class TrAddressDataService {
  private static readonly BASE = 'assets/geo';

  private iller$?: Observable<TrIl[]>;
  private ilceler$?: Observable<TrIlce[]>;
  private index$?: Observable<MahalleIndex>;
  private readonly mahallePartCache = new Map<number, Observable<TrMahalle[]>>();

  constructor(private readonly http: HttpClient) {}

  /** İlk çağrıda `iller.json`; sonraki çağrılar bellek cache. */
  getIller(): Observable<TrIl[]> {
    if (!this.iller$) {
      this.iller$ = this.http
        .get<TrIl[]>(`${TrAddressDataService.BASE}/iller.json`)
        .pipe(shareReplay(1));
    }
    return this.iller$;
  }

  /** `ilceler.json` bir kez lazy; bellek filtre `sehir_id`. */
  getIlceler(sehirId: string): Observable<TrIlce[]> {
    return this.loadIlceler().pipe(
      map((all) => all.filter((row) => row.sehir_id === String(sehirId))),
    );
  }

  /**
   * İlgili `mahalleler-N.json` parçalarını index ile lazy yükler;
   * aynı parça / aynı `ilceId` için ikinci HTTP yok.
   */
  getMahalleler(ilceId: string): Observable<TrMahalle[]> {
    const id = String(ilceId);
    return this.loadIndex().pipe(
      switchMap((index) => {
        const parts = index[id] ?? [];
        if (parts.length === 0) {
          return of([] as TrMahalle[]);
        }
        return forkJoin(parts.map((part) => this.loadMahallePart(part))).pipe(
          map((chunks) =>
            chunks.reduce<TrMahalle[]>((acc, chunk) => {
              for (const row of chunk) {
                if (row.ilce_id === id) {
                  acc.push(row);
                }
              }
              return acc;
            }, []),
          ),
        );
      }),
    );
  }

  private loadIlceler(): Observable<TrIlce[]> {
    if (!this.ilceler$) {
      this.ilceler$ = this.http
        .get<TrIlce[]>(`${TrAddressDataService.BASE}/ilceler.json`)
        .pipe(shareReplay(1));
    }
    return this.ilceler$;
  }

  private loadIndex(): Observable<MahalleIndex> {
    if (!this.index$) {
      this.index$ = this.http
        .get<MahalleIndex>(`${TrAddressDataService.BASE}/mahalle-index.json`)
        .pipe(shareReplay(1));
    }
    return this.index$;
  }

  private loadMahallePart(part: number): Observable<TrMahalle[]> {
    let cached = this.mahallePartCache.get(part);
    if (!cached) {
      cached = this.http
        .get<TrMahalle[]>(`${TrAddressDataService.BASE}/mahalleler-${part}.json`)
        .pipe(shareReplay(1));
      this.mahallePartCache.set(part, cached);
    }
    return cached;
  }
}
