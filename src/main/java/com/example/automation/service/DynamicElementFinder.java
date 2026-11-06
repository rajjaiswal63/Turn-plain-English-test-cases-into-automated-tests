package com.example.automation.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

@Service
public class DynamicElementFinder {
    private static final int MAX_CANDIDATES = 500;
    private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    public Match find(WebDriver driver, String action, String target) {
        if (target == null || target.isBlank()) throw new AutomationException("AI plan did not identify the element to " + action + ".");
        List<Candidate> candidates = inspect(driver).stream()
                .filter(c -> eligible(c, action))
                .map(c -> new Candidate(c.data, c.element, score(c.data, action, target)))
                .filter(c -> c.score > 0)
                .sorted(Comparator.comparingInt(Candidate::score).reversed())
                .toList();
        if (candidates.isEmpty()) {
            throw new AutomationException("Could not identify a visible " + action + " target matching '" + target + "'.");
        }
        Candidate best = candidates.get(0);
        int second = candidates.size() > 1 ? candidates.get(1).score : 0;
        if (best.score < 8 || (!action.equals("verify_visible") && second > 0 && best.score - second < 3)) {
            String examples = candidates.stream().limit(4).map(c -> describe(c.data)).toList().toString();
            throw new AutomationException("Element target '" + target + "' is ambiguous or a weak match. Candidates: " + examples);
        }
        return new Match(best.element, describe(best.data), best.score, best.data);
    }

    public String pageSnapshot(WebDriver driver) {
        List<ElementInfo> infos = inspect(driver);
        StringBuilder result = new StringBuilder();
        for (ElementInfo info : infos) {
            Map<String, String> d = info.data;
            boolean useful = bool(d, "clickable") || bool(d, "fillable") || bool(d, "selectable")
                    || !d.getOrDefault("text", "").isBlank() || !d.getOrDefault("label", "").isBlank();
            if (!useful) continue;
            result.append(describe(d)).append('\n');
            if (result.length() >= 12_000) break;
        }
        return result.isEmpty() ? "No visible text or controls found." : result.toString();
    }

    public boolean hasVisibleText(WebDriver driver, String phrase) {
        if (phrase == null || phrase.isBlank()) return false;
        String expected = normalize(phrase);
        Object visibleText = ((JavascriptExecutor) driver).executeScript(
                "return document.body ? (document.body.innerText || '') : ''; ");
        return visibleText != null && normalize(visibleText.toString()).contains(expected);
    }

    private List<ElementInfo> inspect(WebDriver driver) {
        String script = """
                const all = Array.from(document.querySelectorAll('body *'));
                const out = [];
                for (const el of all) {
                  if (out.length >= arguments[0]) break;
                  const style = getComputedStyle(el);
                  if (!el.getClientRects().length || style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) continue;
                  const tag = (el.tagName || '').toLowerCase();
                  const type = (el.getAttribute('type') || '').toLowerCase();
                  const role = (el.getAttribute('role') || '').toLowerCase();
                  const text = (el.innerText || '').trim().replace(/\\s+/g, ' ').slice(0, 240);
                  const ariaDescription = el.getAttribute('aria-description') || '';
                  const ariaDescribedBy = (el.getAttribute('aria-describedby') || '').split(/\\s+/).filter(Boolean)
                    .map(id => document.getElementById(id)?.innerText || '').join(' ').trim();
                  const labels = el.labels ? Array.from(el.labels).map(x => (x.innerText || '').trim()).join(' ') : '';
                  const labelledBy = (el.getAttribute('aria-labelledby') || '').split(/\\s+/).filter(Boolean)
                    .map(id => document.getElementById(id)?.innerText || '').join(' ').trim();
                  const href = el.href || '';
                  const alt = el.getAttribute('alt') || el.querySelector('img[alt]')?.getAttribute('alt') || '';
                  const accessible = el.getAttribute('aria-label') || labelledBy || labels || alt || el.getAttribute('title') || text;
                  const fillable = ((tag === 'input' && !['hidden','submit','button','reset','image','file'].includes(type)) || tag === 'textarea' || el.isContentEditable || role === 'textbox');
                  const selectable = tag === 'select' || role === 'combobox';
                  const clickable = tag === 'a' || tag === 'button' || ['button','link','menuitem','menuitemradio','option','tab','checkbox','radio','switch'].includes(role) || (tag === 'input' && ['button','submit','reset'].includes(type)) || el.hasAttribute('onclick') || (el.tabIndex >= 0 && style.cursor === 'pointer');
                  const semantic = ['h1','h2','h3','label','main','p','li'].includes(tag) || Boolean(el.getAttribute('aria-label')) || Boolean(role);
                  if (!clickable && !fillable && !selectable && !semantic && !text) continue;
                  out.push({element:el, tag, type, role, id:el.id || '', name:el.getAttribute('name') || '',
                    placeholder:el.getAttribute('placeholder') || '', aria:el.getAttribute('aria-label') || '',
                    ariaDescription, ariaDescribedBy, accessible, alt, labelledBy, label:labels,
                    title:el.getAttribute('title') || '', text, href,
                    clickable:String(clickable), fillable:String(fillable), selectable:String(selectable)});
                }
                return out;
                """;
        Object raw = ((JavascriptExecutor) driver).executeScript(script, MAX_CANDIDATES);
        if (!(raw instanceof List<?> list)) return List.of();
        List<ElementInfo> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Object element = map.get("element");
            if (!(element instanceof WebElement webElement)) continue;
            Map<String, String> data = new LinkedHashMap<>();
            map.forEach((k, v) -> { if (k instanceof String key && v != null && !key.equals("element")) data.put(key, v.toString()); });
            result.add(new ElementInfo(data, webElement));
        }
        return result;
    }

    private boolean eligible(ElementInfo c, String action) {
        return switch (action) {
            case "click" -> bool(c.data, "clickable");
            case "fill" -> bool(c.data, "fillable");
            case "select" -> bool(c.data, "selectable");
            case "verify_visible" -> true;
            default -> false;
        };
    }

    private int score(Map<String, String> d, String action, String target) {
        String normalizedTarget = normalize(target);
        Set<String> targetWords = new HashSet<>(List.of(normalizedTarget.split(" ")));
        int score = 0;
        score += matchScore(d.get("aria"), normalizedTarget, targetWords, 12);
        score += matchScore(d.get("accessible"), normalizedTarget, targetWords, 11);
        score += matchScore(d.get("ariaDescription"), normalizedTarget, targetWords, 8);
        score += matchScore(d.get("ariaDescribedBy"), normalizedTarget, targetWords, 8);
        score += matchScore(d.get("labelledBy"), normalizedTarget, targetWords, 11);
        score += matchScore(d.get("label"), normalizedTarget, targetWords, 10);
        score += matchScore(d.get("placeholder"), normalizedTarget, targetWords, 10);
        score += matchScore(d.get("text"), normalizedTarget, targetWords, 9);
        score += matchScore(d.get("name"), normalizedTarget, targetWords, 8);
        score += matchScore(d.get("id"), normalizedTarget, targetWords, 7);
        score += matchScore(d.get("title"), normalizedTarget, targetWords, 8);
        score += matchScore(d.get("role"), normalizedTarget, targetWords, 5);
        score += matchScore(d.get("type"), normalizedTarget, targetWords, 5);
        if (action.equals("verify_visible") && Set.of("h1", "h2", "h3", "label").contains(d.get("tag"))) score += 2;
        if (normalizedTarget.contains("email") && "email".equals(d.get("type"))) score += 10;
        if (normalizedTarget.contains("password") && "password".equals(d.get("type"))) score += 10;
        if (normalizedTarget.contains("search") && "search".equals(d.get("type"))) score += 6;
        if (action.equals("click") && !d.getOrDefault("href", "").isBlank()) score += 1;
        return score;
    }

    private int matchScore(String raw, String target, Set<String> targetWords, int weight) {
        if (raw == null || raw.isBlank()) return 0;
        String text = normalize(raw);
        if (text.equals(target)) return weight;
        if (text.contains(target) || target.contains(text)) return Math.max(2, weight - 2);
        Set<String> actualWords = new HashSet<>(List.of(text.split(" ")));
        long matching = targetWords.stream().filter(w -> w.length() > 1 && actualWords.contains(w)).count();
        return matching == 0 ? 0 : (int) Math.round(weight * (double) matching / Math.max(1, targetWords.size()) * 0.65);
    }

    private String describe(Map<String, String> d) {
        return "<" + d.getOrDefault("tag", "element") + ">"
                + field("role", d.get("role")) + field("type", d.get("type")) + field("text", d.get("text"))
                + field("label", d.get("label")) + field("aria-label", d.get("aria"))
                + field("accessible-name", d.get("accessible")) + field("aria-description", d.get("ariaDescription"))
                + field("placeholder", d.get("placeholder")) + field("name", d.get("name"))
                + field("id", d.get("id"));
    }

    private String field(String key, String value) { return value == null || value.isBlank() ? "" : " " + key + "='" + value + "'"; }
    private boolean bool(Map<String, String> d, String key) { return Boolean.parseBoolean(d.getOrDefault(key, "false")); }
    private String normalize(String value) {
        String normalized = WORDS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT))
                .replaceAll(" ").trim().replaceAll(" +", " ");
        return normalized.replaceAll("\\b(sign in|log in)\\b", "login")
                .replaceAll("\\b(sign out|log out)\\b", "logout");
    }

    private record ElementInfo(Map<String, String> data, WebElement element) { }
    private record Candidate(Map<String, String> data, WebElement element, int score) { }
    public record Match(WebElement element, String description, int score, Map<String, String> attributes) { }
}
