import { createRouter, createWebHistory } from "vue-router";
import HomeView from "../views/HomeView.vue";
import ReminderScenarioView from "../views/ReminderScenarioView.vue";
import RecurringTodoScenarioView from "../views/RecurringTodoScenarioView.vue";
import InboxCapabilityView from "../views/InboxCapabilityView.vue";
import OpsCapabilityView from "../views/OpsCapabilityView.vue";

const routes = [
  { path: "/", name: "home", component: HomeView },
  { path: "/reminders", name: "reminders", component: ReminderScenarioView },
  { path: "/todos", name: "todos", component: RecurringTodoScenarioView },
  { path: "/inbox", name: "inbox", component: InboxCapabilityView },
  { path: "/ops", name: "ops", component: OpsCapabilityView },
  { path: "/scenarios/reminder", redirect: "/reminders" },
  { path: "/scenarios/recurring-todo", redirect: "/todos" },
  { path: "/capabilities/inbox", redirect: "/inbox" },
  { path: "/capabilities/ops", redirect: "/ops" },
  { path: "/capabilities/calendar", redirect: "/reminders" },
  { path: "/:pathMatch(.*)*", redirect: "/" },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
