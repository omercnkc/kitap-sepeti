import { CategoryResponse } from '../../../core/models';
import {
  buildCategoryTree,
  buildParentOptions,
} from './admin-categories-page.component';

describe('admin category tree helpers', () => {
  const items: CategoryResponse[] = [
    {
      id: 'root',
      name: 'Roman',
      slug: 'roman',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 'child',
      name: 'Polisiye',
      slug: 'polisiye',
      parentId: 'root',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
    {
      id: 'grandchild',
      name: 'Noir',
      slug: 'noir',
      parentId: 'child',
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    },
  ];

  it('buildCategoryTree nests children under parents', () => {
    const tree = buildCategoryTree(items);
    expect(tree.length).toBe(1);
    expect(tree[0].id).toBe('root');
    expect(tree[0].children[0].id).toBe('child');
    expect(tree[0].children[0].children[0].id).toBe('grandchild');
  });

  it('buildParentOptions excludes self and descendants', () => {
    const options = buildParentOptions(items, 'child');
    const ids = options.map((o) => o.id);
    expect(ids).toContain('root');
    expect(ids).not.toContain('child');
    expect(ids).not.toContain('grandchild');
  });
});
