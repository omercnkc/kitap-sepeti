import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminGuard } from './core/guards/admin.guard';
import { AuthGuard } from './core/guards/auth.guard';
import { GuestGuard } from './core/guards/guest.guard';
import { ShellComponent } from './layout/shell/shell.component';
import { NotFoundComponent } from './layout/not-found/not-found.component';

const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'books' },
      {
        path: 'books',
        loadChildren: () =>
          import('./features/catalog/catalog.module').then((m) => m.CatalogModule),
      },
      {
        path: 'login',
        canActivate: [GuestGuard],
        loadChildren: () => import('./features/auth/login.module').then((m) => m.LoginModule),
      },
      {
        path: 'register',
        canActivate: [GuestGuard],
        loadChildren: () =>
          import('./features/auth/register.module').then((m) => m.RegisterModule),
      },
      {
        path: 'cart',
        canActivate: [AuthGuard],
        loadChildren: () => import('./features/cart/cart.module').then((m) => m.CartModule),
      },
      {
        path: 'checkout',
        canActivate: [AuthGuard],
        loadChildren: () =>
          import('./features/checkout/checkout.module').then((m) => m.CheckoutModule),
      },
      {
        path: 'orders',
        canActivate: [AuthGuard],
        loadChildren: () => import('./features/orders/orders.module').then((m) => m.OrdersModule),
      },
      {
        path: 'account',
        canActivate: [AuthGuard],
        loadChildren: () =>
          import('./features/account/account.module').then((m) => m.AccountModule),
      },
      {
        path: 'notifications',
        canActivate: [AuthGuard],
        loadChildren: () =>
          import('./features/notifications/notifications.module').then((m) => m.NotificationsModule),
      },
      {
        path: 'admin',
        canActivate: [AdminGuard],
        canLoad: [AdminGuard],
        loadChildren: () => import('./features/admin/admin.module').then((m) => m.AdminModule),
      },
      { path: '**', component: NotFoundComponent },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule {}
