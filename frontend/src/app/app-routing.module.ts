import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
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
        loadChildren: () => import('./features/auth/login.module').then((m) => m.LoginModule),
      },
      {
        path: 'register',
        loadChildren: () =>
          import('./features/auth/register.module').then((m) => m.RegisterModule),
      },
      {
        path: 'cart',
        loadChildren: () => import('./features/cart/cart.module').then((m) => m.CartModule),
      },
      {
        path: 'checkout',
        loadChildren: () =>
          import('./features/checkout/checkout.module').then((m) => m.CheckoutModule),
      },
      {
        path: 'orders',
        loadChildren: () => import('./features/orders/orders.module').then((m) => m.OrdersModule),
      },
      {
        path: 'account',
        loadChildren: () =>
          import('./features/account/account.module').then((m) => m.AccountModule),
      },
      {
        path: 'notifications',
        loadChildren: () =>
          import('./features/notifications/notifications.module').then((m) => m.NotificationsModule),
      },
      {
        path: 'admin',
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
