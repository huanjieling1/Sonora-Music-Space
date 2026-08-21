package com.example.agent.agent.goal;

import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.GoalConstraint;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative local parser for explicit music goals, connectors, entities, counts and missing slots. */
@Component
public final class DeterministicMusicGoalParser {
    private static final Pattern CONNECTOR = Pattern.compile(
            "同时|然后|接着|随后|之后|再(?=推荐|搜索|查询|查找|播放|放|加入|创建|收藏|把|将)|"
                    + "并(?=推荐|搜索|查询|查找|播放|放|加入|创建|收藏|把|将)|[，,；;]");
    private static final Pattern CONDITIONAL = Pattern.compile("^(?:如果|若)(.+?)(?:就|则)(.+)$");
    private static final Pattern SEPARATE = Pattern.compile(
            "^分别(推荐|搜索|查询|查找|播放)(.+?)和(.+)$");
    private static final Pattern FAVORITE_ARTIST = Pattern.compile(
            "最喜欢的(?:歌手|艺人|乐队|组合)|最爱的(?:歌手|艺人|乐队|组合)|最常听的(?:歌手|艺人|乐队|组合)");
    private static final Pattern PROFILE_WORD = Pattern.compile("资料|档案|介绍|信息|详情|生涯|成就|曲风|风格");
    private static final Pattern TRACK_TITLE = Pattern.compile("《([^》]{1,100})》|[“\"]([^”\"]{1,100})[”\"]");
    private static final Pattern ARTIST_PREFIX = Pattern.compile(
            "(?:歌手|艺人|乐队|组合)\\s*([A-Za-z][A-Za-z0-9 ._·-]{0,39}|[\\p{IsHan}]{2,12})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_POSSESSIVE = Pattern.compile(
            "([A-Za-z][A-Za-z0-9 ._·-]{0,39}|[\\p{IsHan}]{2,12})\\s*的(?:歌|歌曲|音乐|资料|档案|专辑|歌单|《)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_ACTION = Pattern.compile(
            "(?:搜索|查询|查找|介绍|找|推荐)\\s*([A-Za-z][A-Za-z0-9 ._·-]{0,39}|[\\p{IsHan}]{2,12})\\s*(?:的|歌手|艺人|乐队)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT = Pattern.compile("([一二两三四五六七八九十\\d]{1,3})\\s*(?:首|个|张|份)");
    private static final Pattern POSITION = Pattern.compile("第([一二两三四五六七八九十\\d]{1,3})(?:首|个|项)");
    private static final Pattern PRONOUN = Pattern.compile("他(?:的)?|她(?:的)?|他们(?:的)?|她们(?:的)?|该歌手|这个歌手|这些歌|它们");

    public UserGoalGraph parse(String request) {
        String original = requireRequest(request);
        List<Clause> clauses = clauses(original);
        ArrayList<GoalNode> goals = new ArrayList<>();
        ArrayList<GoalRelation> relations = new ArrayList<>();
        int sequence = 1;
        GoalNode previous = null;
        for (Clause clause : clauses) {
            List<GoalNode> parsed = goals(clause.text(), sequence);
            sequence += parsed.size();
            if (parsed.isEmpty()) continue;
            GoalNode first = parsed.get(0);
            if (previous != null) relations.add(relation(previous, first, clause));
            for (int index = 1; index < parsed.size(); index++) {
                relations.add(new GoalRelation(parsed.get(index - 1).id(), parsed.get(index).id(),
                        GoalRelation.Type.DEPENDS_ON, null, "同一子目标中的前置结果依赖"));
            }
            goals.addAll(parsed);
            previous = parsed.get(parsed.size() - 1);
        }
        if (goals.isEmpty()) goals.add(unknownGoal(original, 1));
        addPronounDependencies(goals, relations);
        return new UserGoalGraph("1.0", UUID.randomUUID(), original, List.copyOf(goals),
                relations.stream().distinct().toList());
    }

    private static List<Clause> clauses(String request) {
        ArrayList<Clause> split = new ArrayList<>();
        Matcher matcher = CONNECTOR.matcher(request);
        int cursor = 0;
        String connector = "";
        while (matcher.find()) {
            addClause(split, request.substring(cursor, matcher.start()), connector);
            connector = matcher.group();
            cursor = matcher.end();
        }
        addClause(split, request.substring(cursor), connector);

        ArrayList<Clause> expanded = new ArrayList<>();
        for (Clause clause : split) {
            Matcher conditional = CONDITIONAL.matcher(clause.text());
            if (conditional.matches()) {
                expanded.add(new Clause(conditional.group(2).strip(), "如果", conditional.group(1).strip()));
                continue;
            }
            Matcher separate = SEPARATE.matcher(clause.text());
            if (separate.matches()) {
                String action = separate.group(1);
                expanded.add(new Clause(action + separate.group(2).strip(), clause.connector(), clause.condition()));
                expanded.add(new Clause(action + separate.group(3).strip(), "同时", ""));
                continue;
            }
            expanded.add(clause);
        }
        return expanded;
    }

    private static void addClause(List<Clause> target, String raw, String connector) {
        String value = raw == null ? "" : raw.strip();
        if (!value.isEmpty()) target.add(new Clause(value, connector, ""));
    }

    private static List<GoalNode> goals(String clause, int sequence) {
        if (FAVORITE_ARTIST.matcher(clause).find() && PROFILE_WORD.matcher(clause).find()) {
            GoalNode resolve = node("goal-" + sequence, "从画像确定最偏好的歌手", GoalOperation.RESOLVE,
                    GoalTargetType.ARTIST,
                    Map.of("profile", ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile")),
                    List.of(), List.of(), false);
            GoalNode lookup = node("goal-" + (sequence + 1), "查询该歌手的资料", GoalOperation.LOOKUP,
                    GoalTargetType.ARTIST, Map.of(), List.of(), List.of(), false);
            return List.of(resolve, lookup);
        }

        GoalOperation operation = operation(clause);
        GoalTargetType target = target(clause, operation);
        LinkedHashMap<String, ValueExpression> inputs = new LinkedHashMap<>();
        ArrayList<GoalConstraint> constraints = new ArrayList<>();
        ArrayList<String> missing = new ArrayList<>();

        String title = trackTitle(clause);
        if (!title.isEmpty()) inputs.put("trackTitle", ValueExpression.literal(ValueType.STRING, title));
        String artist = artist(clause);
        if (!artist.isEmpty()) inputs.put("artistName", ValueExpression.literal(ValueType.STRING, artist));
        Integer count = number(COUNT, clause);
        if (count != null) {
            inputs.put("limit", ValueExpression.literal(ValueType.INTEGER, count));
            constraints.add(new GoalConstraint("count", GoalConstraint.Operator.EQUALS,
                    ValueExpression.literal(ValueType.INTEGER, count), true, "保留用户明确数量"));
        }
        Integer position = number(POSITION, clause);
        if (position != null) inputs.put("position", ValueExpression.literal(ValueType.INTEGER, position));
        Matcher reference = PRONOUN.matcher(clause);
        if (reference.find()) {
            inputs.put("reference", ValueExpression.literal(ValueType.STRING, normalizeReference(reference.group())));
        }
        for (String scene : scenes(clause)) {
            constraints.add(new GoalConstraint("scene", GoalConstraint.Operator.CONTAINS,
                    ValueExpression.literal(ValueType.STRING, scene), true, "用户明确场景"));
        }

        if ((operation == GoalOperation.LOOKUP || operation == GoalOperation.SEARCH)
                && target == GoalTargetType.ARTIST && artist.isEmpty()) missing.add("artistName");
        if ((operation == GoalOperation.SEARCH || operation == GoalOperation.RECOMMEND)
                && target == GoalTargetType.NONE) missing.add("target");
        if (operation == GoalOperation.UNKNOWN) missing.add("action");
        if (operation == GoalOperation.QUEUE_ADD && !PRONOUN.matcher(clause).find()
                && !clause.matches(".*(?:推荐|搜索|结果|歌曲|歌).*")) missing.add("tracks");

        boolean confirmation = switch (operation) {
            case PLAY, QUEUE_ADD, QUEUE_REMOVE, CREATE, UPDATE, DELETE -> true;
            default -> false;
        };
        return List.of(node("goal-" + sequence, goalTitle(operation, target, clause), operation, target,
                inputs, constraints, missing, confirmation));
    }

    private static GoalNode node(String id, String title, GoalOperation operation, GoalTargetType target,
                                 Map<String, ValueExpression> inputs, List<GoalConstraint> constraints,
                                 List<String> missing, boolean confirmation) {
        String subject = switch (target) {
            case ARTIST -> "$.artist";
            case TRACK -> "$.tracks";
            case PLAYLIST -> "$.playlists";
            case PROFILE -> "$.profile";
            case CHART -> "$.entries";
            case QUEUE -> "$.queue";
            default -> "$.result";
        };
        AcceptanceCriterion criterion = new AcceptanceCriterion(id + "-output",
                confirmation ? AcceptanceCriterion.Type.STATE_CHANGE : AcceptanceCriterion.Type.OUTPUT_PRESENT,
                subject, null, true, "目标必须产生可验证结果", Map.of());
        return new GoalNode(id, title, operation, target, inputs, constraints,
                List.of(criterion), missing, confirmation);
    }

    private static GoalNode unknownGoal(String request, int sequence) {
        return node("goal-" + sequence, request, GoalOperation.UNKNOWN, GoalTargetType.NONE,
                Map.of(), List.of(), List.of("action", "target"), false);
    }

    private static GoalOperation operation(String clause) {
        String value = clause.toLowerCase(Locale.ROOT);
        if (value.matches(".*(?:加入|添加到|放进).*(?:队列|播放列表).*$")) return GoalOperation.QUEUE_ADD;
        if (value.matches(".*(?:移出|删除|清空).*(?:队列|播放列表).*$")) return GoalOperation.QUEUE_REMOVE;
        if (value.matches(".*(?:收藏|喜欢这首).*$")) return GoalOperation.UPDATE;
        if (value.matches(".*(?:创建|新建|制作|生成).*(?:歌单|播放列表).*$")) return GoalOperation.CREATE;
        if (value.matches(".*(?:播放|放一下|放这|听一下|听这|开始听).*$")) return GoalOperation.PLAY;
        if (value.matches(".*(?:推荐|来点|来首|来一些|猜你喜欢).*$")) return GoalOperation.RECOMMEND;
        if (PROFILE_WORD.matcher(value).find()) {
            return GoalOperation.LOOKUP;
        }
        if (value.matches(".*(?:分析|总结|查看).*(?:画像|偏好).*$")) return GoalOperation.ANALYZE;
        if (value.matches(".*(?:搜索|搜一下|查询|查找|找).*$")) return GoalOperation.SEARCH;
        return GoalOperation.UNKNOWN;
    }

    private static GoalTargetType target(String clause, GoalOperation operation) {
        if (clause.matches(".*(?:队列|播放列表).*")
                && (operation == GoalOperation.QUEUE_ADD || operation == GoalOperation.QUEUE_REMOVE)) {
            return GoalTargetType.QUEUE;
        }
        if (clause.matches(".*(?:画像|音乐偏好|收听偏好).*$")) return GoalTargetType.PROFILE;
        if (clause.matches(".*(?:排行榜|榜单|排行|趋势).*$")) return GoalTargetType.CHART;
        if (clause.matches(".*(?:歌单|播放列表).*$")) return GoalTargetType.PLAYLIST;
        if (operation == GoalOperation.LOOKUP && PROFILE_WORD.matcher(clause).find()
                && !clause.matches(".*(?:专辑|album|歌曲|音乐|曲目|歌单|播放列表).*$")) {
            return GoalTargetType.ARTIST;
        }
        if (clause.matches(".*(?:歌手|艺人|乐队|组合|artist|singer|band).*$")
                && !clause.matches(".*(?:他的歌|她的歌|歌手作品).*$")) return GoalTargetType.ARTIST;
        if (clause.matches(".*(?:专辑|album).*$")) return GoalTargetType.ALBUM;
        if (operation == GoalOperation.PLAY && POSITION.matcher(clause).find()) return GoalTargetType.SEARCH_RESULT;
        if (clause.matches(".*(?:歌|歌曲|音乐|曲目|《.+》|他的|她的).*$")) return GoalTargetType.TRACK;
        return GoalTargetType.NONE;
    }

    private static GoalRelation relation(GoalNode source, GoalNode target, Clause clause) {
        if (!clause.condition().isEmpty() || "如果".equals(clause.connector())) {
            return new GoalRelation(source.id(), target.id(), GoalRelation.Type.CONDITIONAL,
                    ValueExpression.literal(ValueType.STRING, clause.condition()), "用户条件分支");
        }
        GoalRelation.Type type = "同时".equals(clause.connector())
                || "分别".equals(clause.connector()) ? GoalRelation.Type.PARALLEL
                : GoalRelation.Type.SEQUENCE;
        return new GoalRelation(source.id(), target.id(), type, null,
                type == GoalRelation.Type.PARALLEL ? "用户要求同时处理" : "保留用户表达的执行顺序");
    }

    private static void addPronounDependencies(List<GoalNode> goals, List<GoalRelation> relations) {
        GoalNode artistProducer = null;
        for (GoalNode goal : goals) {
            if (goal.targetType() == GoalTargetType.ARTIST
                    && (goal.operation() == GoalOperation.RESOLVE || goal.operation() == GoalOperation.LOOKUP)) {
                if (goal.operation() == GoalOperation.RESOLVE || artistProducer == null) artistProducer = goal;
                continue;
            }
            GoalNode producer = artistProducer;
            if (producer != null && goal.inputs().containsKey("reference")
                    && relations.stream().noneMatch(value -> value.sourceGoalId().equals(producer.id())
                    && value.targetGoalId().equals(goal.id()))) {
                relations.add(new GoalRelation(producer.id(), goal.id(), GoalRelation.Type.DEPENDS_ON,
                        null, "指代词绑定到前置歌手实体"));
            }
        }
    }

    private static String goalTitle(GoalOperation operation, GoalTargetType target, String clause) {
        return switch (operation) {
            case RESOLVE -> "解析" + targetName(target);
            case LOOKUP -> "查询" + targetName(target) + "资料";
            case SEARCH -> "搜索" + targetName(target);
            case RECOMMEND -> "推荐" + targetName(target) + (PRONOUN.matcher(clause).find() ? "（引用前置实体）" : "");
            case ANALYZE -> "分析" + targetName(target);
            case PLAY -> "播放" + targetName(target);
            case QUEUE_ADD -> "加入播放队列" + (PRONOUN.matcher(clause).find() ? "（引用前置结果）" : "");
            case QUEUE_REMOVE -> "从播放队列移除";
            case CREATE -> "创建" + targetName(target);
            case UPDATE -> "更新" + targetName(target);
            default -> clause;
        };
    }

    private static String targetName(GoalTargetType target) {
        return switch (target) {
            case TRACK, SEARCH_RESULT -> "歌曲";
            case ARTIST -> "歌手";
            case ALBUM -> "专辑";
            case PLAYLIST -> "歌单";
            case PROFILE -> "音乐画像";
            case CHART -> "榜单";
            case QUEUE -> "播放队列";
            default -> "音乐目标";
        };
    }

    private static String trackTitle(String clause) {
        Matcher matcher = TRACK_TITLE.matcher(clause);
        if (!matcher.find()) return "";
        return matcher.group(1) != null ? matcher.group(1).strip() : matcher.group(2).strip();
    }

    private static String artist(String clause) {
        for (Pattern pattern : List.of(ARTIST_PREFIX, ARTIST_ACTION, ARTIST_POSSESSIVE)) {
            Matcher matcher = pattern.matcher(clause);
            if (matcher.find()) {
                String candidate = matcher.group(1).strip();
                if (!candidate.matches("资料|档案|信息|详情|作品|歌曲|歌单|最喜欢|最常听")
                        && !candidate.matches(".*(?:首|他|她|它|我|你|这|该).*")
                        && !candidate.matches("^[一二两三四五六七八九十\\d].*")) return candidate;
            }
        }
        return "";
    }

    private static String normalizeReference(String reference) {
        return reference.endsWith("的") ? reference.substring(0, reference.length() - 1) : reference;
    }

    private static Integer number(Pattern pattern, String clause) {
        Matcher matcher = pattern.matcher(clause);
        return matcher.find() ? chineseNumber(matcher.group(1)) : null;
    }

    private static int chineseNumber(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return switch (raw) {
                case "一" -> 1; case "二", "两" -> 2; case "三" -> 3; case "四" -> 4;
                case "五" -> 5; case "六" -> 6; case "七" -> 7; case "八" -> 8;
                case "九" -> 9; case "十" -> 10; default -> 1;
            };
        }
    }

    private static List<String> scenes(String clause) {
        return List.of("跑步", "健身", "学习", "工作", "通勤", "开车", "睡前", "深夜", "派对", "旅行")
                .stream().filter(clause::contains).toList();
    }

    private static String requireRequest(String request) {
        if (request == null || request.isBlank()) throw new IllegalArgumentException("用户请求不能为空");
        return request.strip();
    }

    private record Clause(String text, String connector, String condition) {
        private Clause {
            text = text == null ? "" : text.strip();
            connector = connector == null ? "" : connector.strip();
            condition = condition == null ? "" : condition.strip();
        }
    }
}
