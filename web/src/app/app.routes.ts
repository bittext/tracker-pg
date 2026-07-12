import { Routes } from '@angular/router';
import { adminGuard } from './guards/admin.guard';
import { authGuard } from './guards/auth.guard';
import { marketsGuard } from './guards/markets.guard';
import { onboardingAdminGuard } from './guards/onboarding-admin.guard';
import { onboardingCompleteGuard } from './guards/onboarding-complete.guard';
import { onboardingCredentialsGuard } from './guards/onboarding-credentials.guard';
import { onboardingMemberIdGuard } from './guards/onboarding-member-id.guard';
import { AdminShellComponent } from './layout/admin-shell/admin-shell.component';
import { LifeShellComponent } from './layout/life-shell/life-shell.component';
import { MarketsShellComponent } from './layout/markets-shell/markets-shell.component';
import { AdminComponent } from './pages/admin/admin.component';
import { ContactComponent } from './pages/contact/contact.component';
import { ExerciseComponent } from './pages/exercise/exercise.component';
import { FinanceComponent } from './pages/finance/finance.component';
import { JournalComponent } from './pages/journal/journal.component';
import { ManagementComponent } from './pages/management/management.component';
import { MarketsAlertsComponent } from './pages/markets/markets-alerts/markets-alerts.component';
import { MarketsExecutionComponent } from './pages/markets/markets-execution/markets-execution.component';
import { MarketsOverviewComponent } from './pages/markets/markets-overview/markets-overview.component';
import { ReportsComponent } from './pages/reports/reports.component';
import { SettingsComponent } from './pages/settings/settings.component';
import { WelcomeComponent } from './pages/welcome/welcome.component';

const lifeGuards = [authGuard, onboardingCompleteGuard];
const marketsGuards = [authGuard, onboardingCompleteGuard, marketsGuard];

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'life/welcome' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'privacy',
    loadComponent: () => import('./pages/privacy-policy/privacy-policy.component').then((m) => m.PrivacyPolicyComponent),
  },
  { path: 'privacy-policy', redirectTo: 'privacy', pathMatch: 'full' },
  {
    path: 'security',
    loadComponent: () =>
      import('./pages/security-program/security-program.component').then((m) => m.SecurityProgramComponent),
  },
  {
    path: 'onboarding/credentials',
    canActivate: [authGuard, onboardingCredentialsGuard],
    loadComponent: () =>
      import('./pages/onboarding/onboarding-credentials.component').then((m) => m.OnboardingCredentialsComponent),
  },
  {
    path: 'onboarding/member-id',
    canActivate: [authGuard, onboardingMemberIdGuard],
    loadComponent: () =>
      import('./pages/onboarding/onboarding-member-id.component').then((m) => m.OnboardingMemberIdComponent),
  },
  {
    path: 'life',
    component: LifeShellComponent,
    canActivate: lifeGuards,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'welcome' },
      { path: 'welcome', component: WelcomeComponent },
      { path: 'exercise', component: ExerciseComponent },
      { path: 'management', component: ManagementComponent },
      { path: 'journal', component: JournalComponent },
      { path: 'contact', component: ContactComponent },
      { path: 'money', component: FinanceComponent, data: { workspace: 'money' } },
      { path: 'settings', component: SettingsComponent },
      { path: 'insights', component: ReportsComponent, data: { section: 'life' } },
      { path: 'insights/exercise', redirectTo: 'insights', pathMatch: 'full' },
      { path: 'insights/management', redirectTo: 'insights', pathMatch: 'full' },
      { path: 'insights/journal', redirectTo: 'insights', pathMatch: 'full' },
      { path: 'insights/banking', redirectTo: 'insights', pathMatch: 'full' },
    ],
  },
  {
    path: 'markets',
    component: MarketsShellComponent,
    canActivate: marketsGuards,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      { path: 'overview', component: MarketsOverviewComponent },
      { path: 'workspace', component: FinanceComponent, data: { workspace: 'trading' } },
      { path: 'research', redirectTo: 'workspace', pathMatch: 'full' },
      { path: 'alerts', component: MarketsAlertsComponent },
      {
        path: 'analytics',
        component: ReportsComponent,
        data: { section: 'markets' },
      },
      { path: 'execution', component: MarketsExecutionComponent },
    ],
  },
  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [authGuard, onboardingAdminGuard, adminGuard],
    children: [{ path: '', component: AdminComponent }],
  },
  {
    path: 'logs',
    canActivate: [adminGuard],
    loadComponent: () => import('./pages/logs/logs.component').then((m) => m.LogsComponent),
  },
  { path: 'welcome', redirectTo: 'life/welcome', pathMatch: 'full' },
  { path: 'exercise', redirectTo: 'life/exercise', pathMatch: 'full' },
  { path: 'finance', redirectTo: 'life/money', pathMatch: 'full' },
  { path: 'management', redirectTo: 'life/management', pathMatch: 'full' },
  { path: 'journal', redirectTo: 'life/journal', pathMatch: 'full' },
  { path: 'reports', redirectTo: 'life/insights', pathMatch: 'full' },
  { path: 'contact', redirectTo: 'life/contact', pathMatch: 'full' },
  { path: '**', redirectTo: 'life/welcome' },
];
