import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  TemplateRef,
  ViewChild,
} from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { forkJoin, Observable, of, Subject } from 'rxjs';
import { finalize, map, switchMap, takeUntil } from 'rxjs/operators';
import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { CategoryResponse, FieldError, PageResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const PAGE_SIZE = 100;

type PageState = 'loading' | 'ready' | 'empty' | 'error';

export interface CategoryTreeNode extends CategoryResponse {
  children: CategoryTreeNode[];
}

export interface ParentOption {
  id: string;
  label: string;
}

@Component({
  selector: 'app-admin-categories-page',
  templateUrl: './admin-categories-page.component.html',
  styleUrls: ['./admin-categories-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminCategoriesPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private modalRef: NgbModalRef | null = null;

  @ViewChild('entityModal') entityModal!: TemplateRef<unknown>;

  pageState: PageState = 'loading';
  flat: CategoryResponse[] = [];
  tree: CategoryTreeNode[] = [];
  parentOptions: ParentOption[] = [];

  editing: CategoryResponse | null = null;
  saving = false;
  busyId: string | null = null;
  fieldErrors: FieldError[] = [];

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    slug: ['', [Validators.pattern(SLUG_PATTERN), Validators.maxLength(120)]],
    parentId: [''],
  });

  constructor(
    private readonly api: AdminCatalogApi,
    private readonly fb: FormBuilder,
    private readonly modal: NgbModal,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly toast: ToastService,
    private readonly cdr: ChangeDetectorRef,
  ) {
    this.form.statusChanges.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.cdr.markForCheck();
    });
  }

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.modalRef?.dismiss();
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackById(_index: number, item: CategoryTreeNode): string {
    return item.id;
  }

  openCreate(parentId: string | null = null): void {
    this.editing = null;
    this.fieldErrors = [];
    this.parentOptions = buildParentOptions(this.flat, null);
    this.form.reset({
      name: '',
      slug: '',
      parentId: parentId || '',
    });
    this.openModal();
  }

  openEdit(item: CategoryResponse): void {
    this.editing = item;
    this.fieldErrors = [];
    this.parentOptions = buildParentOptions(this.flat, item.id);
    this.form.reset({
      name: item.name,
      slug: item.slug,
      parentId: item.parentId || '',
    });
    this.openModal();
  }

  onFormCancel(): void {
    this.modalRef?.dismiss();
  }

  get canSave(): boolean {
    return this.form.valid && !this.saving;
  }

  onFormSubmit(): void {
    this.form.markAllAsTouched();
    this.cdr.markForCheck();
    if (!this.canSave) {
      return;
    }

    const raw = this.form.getRawValue() as {
      name: string;
      slug: string;
      parentId: string;
    };
    const name = raw.name.trim();
    const slug = raw.slug.trim();
    const parentId = raw.parentId ? raw.parentId : null;

    this.saving = true;
    this.fieldErrors = [];
    this.cdr.markForCheck();

    const request$ = this.editing
      ? this.updateAndMaybeMove(this.editing, name, slug, parentId)
      : this.api.createCategory({
          name,
          ...(slug ? { slug } : {}),
          ...(parentId ? { parentId } : {}),
        });

    request$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.saving = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => {
          this.toast.success(this.editing ? 'Kategori güncellendi' : 'Kategori eklendi');
          this.modalRef?.close();
          this.load();
        },
        error: (err: unknown) => {
          const problem = toProblemDetail(err);
          this.fieldErrors = problem.errors ?? [];
          this.cdr.markForCheck();
        },
      });
  }

  onDelete(item: CategoryResponse): void {
    if (this.busyId) {
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Kategoriyi sil',
        message: `"${item.name}" silinecek. Emin misiniz?`,
        confirmLabel: 'Sil',
        cancelLabel: 'Vazgeç',
        confirmButtonClass: 'btn-danger',
      })
      .pipe(takeUntil(this.destroy$))
      .subscribe((ok) => {
        if (!ok) {
          return;
        }
        this.busyId = item.id;
        this.cdr.markForCheck();
        this.api
          .deleteCategory(item.id)
          .pipe(
            takeUntil(this.destroy$),
            finalize(() => {
              this.busyId = null;
              this.cdr.markForCheck();
            }),
          )
          .subscribe({
            next: () => {
              this.toast.success('Kategori silindi');
              this.load();
            },
            error: () => undefined,
          });
      });
  }

  private updateAndMaybeMove(
    current: CategoryResponse,
    name: string,
    slug: string,
    parentId: string | null,
  ): Observable<CategoryResponse> {
    const body: { name?: string; slug?: string } = { name };
    if (slug) {
      body.slug = slug;
    }

    const currentParent = current.parentId || null;
    const parentChanged = parentId !== currentParent;

    return this.api.updateCategory(current.id, body).pipe(
      switchMap((updated) => {
        if (!parentChanged) {
          return of(updated);
        }
        return this.api.moveCategory(current.id, { parentId });
      }),
    );
  }

  private load(): void {
    this.pageState = 'loading';
    this.cdr.markForCheck();
    this.loadAllCategories()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (items) => {
          this.flat = items;
          this.tree = buildCategoryTree(items);
          this.pageState = items.length === 0 ? 'empty' : 'ready';
          this.cdr.markForCheck();
        },
        error: () => {
          this.pageState = 'error';
          this.cdr.markForCheck();
        },
      });
  }

  private loadAllCategories(): Observable<CategoryResponse[]> {
    return this.api.listCategories({ page: 0, size: PAGE_SIZE }).pipe(
      switchMap((first) => {
        if (first.totalPages <= 1) {
          return of(first.items);
        }
        const rest: Observable<PageResponse<CategoryResponse>>[] = [];
        for (let p = 1; p < first.totalPages; p += 1) {
          rest.push(this.api.listCategories({ page: p, size: PAGE_SIZE }));
        }
        return forkJoin(rest).pipe(
          map((pages) => first.items.concat(...pages.map((page) => page.items))),
        );
      }),
    );
  }

  private openModal(): void {
    if (!this.entityModal) {
      return;
    }
    this.modalRef?.dismiss();
    this.modalRef = this.modal.open(this.entityModal, {
      centered: true,
      backdrop: 'static',
    });
    this.modalRef.result.finally(() => {
      this.modalRef = null;
      this.saving = false;
      this.fieldErrors = [];
      this.editing = null;
      this.cdr.markForCheck();
    });
    this.cdr.markForCheck();
  }
}

export function buildCategoryTree(items: CategoryResponse[]): CategoryTreeNode[] {
  const byId = new Map<string, CategoryTreeNode>();
  items.forEach((item) => {
    byId.set(item.id, { ...item, children: [] });
  });

  const roots: CategoryTreeNode[] = [];
  byId.forEach((node) => {
    const parentId = node.parentId;
    if (parentId && byId.has(parentId)) {
      byId.get(parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  });

  const sortRec = (nodes: CategoryTreeNode[]): void => {
    nodes.sort((a, b) => a.name.localeCompare(b.name, 'tr'));
    nodes.forEach((n) => sortRec(n.children));
  };
  sortRec(roots);
  return roots;
}

/** Parent seçenekleri; `excludeId` ve alt ağacı hariç. */
export function buildParentOptions(
  items: CategoryResponse[],
  excludeId: string | null,
): ParentOption[] {
  const tree = buildCategoryTree(items);
  const excluded = new Set<string>();
  if (excludeId) {
    collectDescendantIds(tree, excludeId, excluded);
    excluded.add(excludeId);
  }

  const options: ParentOption[] = [];
  const walk = (nodes: CategoryTreeNode[], depth: number): void => {
    nodes.forEach((node) => {
      if (!excluded.has(node.id)) {
        const prefix = depth > 0 ? `${'— '.repeat(depth)}` : '';
        options.push({ id: node.id, label: `${prefix}${node.name}` });
      }
      walk(node.children, depth + 1);
    });
  };
  walk(tree, 0);
  return options;
}

function collectDescendantIds(
  roots: CategoryTreeNode[],
  id: string,
  out: Set<string>,
): void {
  const find = (nodes: CategoryTreeNode[]): CategoryTreeNode | null => {
    for (const n of nodes) {
      if (n.id === id) {
        return n;
      }
      const nested = find(n.children);
      if (nested) {
        return nested;
      }
    }
    return null;
  };
  const node = find(roots);
  if (!node) {
    return;
  }
  const walk = (n: CategoryTreeNode): void => {
    n.children.forEach((c) => {
      out.add(c.id);
      walk(c);
    });
  };
  walk(node);
}
