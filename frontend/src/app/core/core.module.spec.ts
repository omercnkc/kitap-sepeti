import { CoreModule, throwIfAlreadyLoaded } from './core.module';

describe('CoreModule', () => {
  it('should allow a single root instance', () => {
    expect(() => new CoreModule(null as unknown as CoreModule)).not.toThrow();
  });

  it('should throw when a parent CoreModule already exists', () => {
    const parent = new CoreModule(null as unknown as CoreModule);
    expect(() => new CoreModule(parent)).toThrowError(/CoreModule already loaded/);
  });

  it('throwIfAlreadyLoaded should no-op when parent is absent', () => {
    expect(() => throwIfAlreadyLoaded(null, 'CoreModule')).not.toThrow();
  });
});
