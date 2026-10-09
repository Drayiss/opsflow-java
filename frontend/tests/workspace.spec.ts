import { test, expect, Page } from "@playwright/test";
async function signIn(page: Page, username: string) {
  await page.goto("/");
  await page.getByRole("button", { name: /Sign in to your workspace/ }).click();
  await page.getByLabel("Username or email").fill(username);
  await page.getByLabel("Password", { exact: true }).fill("demo-password");
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Incident overview" }),
  ).toBeVisible();
}
test("OIDC login, incident workflow, activity, notifications and settings", async ({
  page,
}) => {
  await signIn(page, "alice");
  await expect(page.getByLabel("Organization", { exact: true })).toContainText(
    "Acme Operations",
  );
  await page.getByRole("button", { name: "New incident" }).click();
  const title = `Browser test ${Date.now()}`;
  await page.getByLabel("Title", { exact: true }).fill(title);
  await page
    .getByLabel("Description")
    .fill("End-to-end workflow verification.");
  await page
    .getByRole("button", { name: "Create incident", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByRole("heading", { name: title })).toBeVisible();
  await dialog
    .getByLabel("Status", { exact: true })
    .selectOption("ACKNOWLEDGED");
  await dialog
    .getByLabel("Assignee", { exact: true })
    .selectOption({ label: "Bob Chen" });
  await dialog.getByRole("button", { name: "Save changes" }).click();
  await expect(dialog.getByLabel("Status", { exact: true })).toHaveValue(
    "ACKNOWLEDGED",
  );
  await dialog
    .getByLabel("Comment", { exact: true })
    .fill("Investigating the dependency.");
  await dialog.getByRole("button", { name: "Post update" }).click();
  await expect(
    dialog.getByText("Investigating the dependency.", { exact: true }),
  ).toBeVisible();
  await dialog.getByLabel("Status", { exact: true }).selectOption("RESOLVED");
  await dialog.getByRole("button", { name: "Save changes" }).click();
  await expect(dialog.getByLabel("Status", { exact: true })).toHaveValue(
    "RESOLVED",
  );
  await page.getByRole("button", { name: "Close dialog" }).click();
  await page.getByRole("button", { name: /Inbox/ }).click();
  await expect(async () => {
    await page.getByRole("button", { name: "Refresh workspace" }).click();
    await expect(
      page.getByRole("button", {
        name: `Created SEV3 incident: ${title}`,
        exact: true,
      }),
    ).toBeVisible();
  }).toPass({ timeout: 20000 });
  await page.getByRole("button", { name: "Team & settings" }).click();
  await expect(
    page.getByRole("heading", { name: "Team members" }),
  ).toBeVisible();
  await expect(page.getByText("Bob Chen", { exact: true })).toBeVisible();
});
test("another tenant has its own workspace", async ({ page }) => {
  await signIn(page, "eve");
  await expect(page.getByLabel("Organization", { exact: true })).toContainText(
    "Northstar Labs",
  );
  await expect(
    page.getByLabel("Organization", { exact: true }),
  ).not.toContainText("Acme Operations");
  await expect(
    page.getByRole("button", {
      name: "Research portal unavailable",
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", {
      name: "Elevated checkout error rate",
      exact: true,
    }),
  ).toHaveCount(0);
});
test("responder can manage incidents but cannot administer memberships", async ({
  page,
}) => {
  await signIn(page, "bob");
  await expect(
    page.getByRole("button", { name: "New incident" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Team & settings" }).click();
  await expect(
    page.getByRole("heading", { name: "Team members" }),
  ).toBeVisible();
  await expect(page.getByLabel("OIDC subject ID")).toHaveCount(0);
});
