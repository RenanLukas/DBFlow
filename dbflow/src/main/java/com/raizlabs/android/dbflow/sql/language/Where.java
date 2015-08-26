package com.raizlabs.android.dbflow.sql.language;

import android.database.Cursor;
import android.database.DatabaseUtils;

import com.raizlabs.android.dbflow.SQLiteCompatibilityUtils;
import com.raizlabs.android.dbflow.config.BaseDatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.Query;
import com.raizlabs.android.dbflow.sql.QueryBuilder;
import com.raizlabs.android.dbflow.sql.SqlUtils;
import com.raizlabs.android.dbflow.sql.builder.ConditionQueryBuilder;
import com.raizlabs.android.dbflow.sql.queriable.ModelQueriable;
import com.raizlabs.android.dbflow.structure.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Description: Defines the SQL WHERE statement of the query.
 */
public class Where<ModelClass extends Model> extends BaseModelQueriable<ModelClass>
        implements Query, ModelQueriable<ModelClass>, Transformable<ModelClass> {

    private static final int VALUE_UNSET = -1;

    /**
     * The first chunk of the SQL statement before this query.
     */
    private final WhereBase<ModelClass> whereBase;
    /**
     * The database manager we run this query on
     */
    private final BaseDatabaseDefinition databaseDefinition;
    /**
     * Helps to build the where statement easily
     */
    private ConditionQueryBuilder<ModelClass> conditionQueryBuilder;

    private final List<NameAlias> groupByList = new ArrayList<>();

    private final List<OrderBy> orderByList = new ArrayList<>();

    /**
     * The SQL HAVING
     */
    private ConditionQueryBuilder<ModelClass> having;
    private int limit = VALUE_UNSET;
    private int offset = VALUE_UNSET;

    /**
     * Constructs this class with the specified {@link com.raizlabs.android.dbflow.config.FlowManager}
     * and {@link From} chunk
     *
     * @param whereBase The FROM or SET statement chunk
     */
    Where(WhereBase<ModelClass> whereBase, SQLCondition... conditions) {
        super(whereBase.getTable());
        this.whereBase = whereBase;
        databaseDefinition = FlowManager.getDatabaseForTable(this.whereBase.getTable());
        conditionQueryBuilder = new ConditionQueryBuilder<>(this.whereBase.getTable());
        having = new ConditionQueryBuilder<>(this.whereBase.getTable());

        conditionQueryBuilder.addConditions(conditions);
    }

    /**
     * Adds a param to the WHERE clause with the custom {@link SQLCondition}
     *
     * @param condition The {@link SQLCondition} to use
     * @return
     */
    public Where<ModelClass> and(SQLCondition condition) {
        conditionQueryBuilder.and(condition);
        return this;
    }

    /**
     * Appends an OR with a Condition to the WHERE clause with the specified {@link SQLCondition}
     *
     * @param condition
     * @return
     */
    public Where<ModelClass> or(SQLCondition condition) {
        conditionQueryBuilder.or(condition);
        return this;
    }

    /**
     * Adds a bunch of {@link Condition} to this builder.
     *
     * @param conditions The list of {@link SQLCondition}
     * @return
     */
    public Where<ModelClass> andThese(List<SQLCondition> conditions) {
        conditionQueryBuilder.addConditions(conditions);
        return this;
    }

    /**
     * Adds a bunch of {@link SQLCondition} to this builder.
     *
     * @param conditions The array of {@link SQLCondition}
     * @return
     */
    public Where<ModelClass> andThese(SQLCondition... conditions) {
        conditionQueryBuilder.addConditions(conditions);
        return this;
    }

    @Override
    public Where<ModelClass> groupBy(NameAlias... columns) {
        Collections.addAll(groupByList, columns);
        return this;
    }

    @Override
    public Where<ModelClass> groupBy(Property... properties) {
        for (Property property : properties) {
            groupByList.add(property.nameAlias);
        }
        return this;
    }

    /**
     * Defines a SQL HAVING statement without the HAVING.
     *
     * @param conditions The array of {@link SQLCondition}
     * @return
     */
    @Override
    public Where<ModelClass> having(SQLCondition... conditions) {
        having.addConditions(conditions);
        return this;
    }

    @Override
    public Where<ModelClass> orderBy(NameAlias nameAlias, boolean ascending) {
        orderByList.add(new OrderBy(nameAlias, ascending));
        return this;
    }

    @Override
    public Where<ModelClass> orderBy(Property property, boolean ascending) {
        orderByList.add(new OrderBy(property.nameAlias, ascending));
        return this;
    }

    @Override
    public Where<ModelClass> limit(int count) {
        this.limit = count;
        return this;
    }

    @Override
    public Where<ModelClass> offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Sets this statement to only specify that it EXISTS
     *
     * @return
     */
    public Where<ModelClass> exists(Where where) {
        //conditionQueryBuilder.addCondition(Condition.exists()
        //        .operation("")
        //        .value(where));
        return this;
    }

    /**
     * Executes a SQL statement that retrieves the count of results in the DB.
     *
     * @return The number of rows this query returns
     */
    public long count() {
        long count;
        if ((whereBase instanceof Set) || whereBase.getQueryBuilderBase() instanceof Delete) {
            count = SQLiteCompatibilityUtils.executeUpdateDelete(databaseDefinition.getWritableDatabase(), getQuery());
        } else {
            count = DatabaseUtils.longForQuery(databaseDefinition.getWritableDatabase(), getQuery(), null);
        }
        return count;
    }

    @Override
    public String getQuery() {
        String fromQuery = whereBase.getQuery();
        QueryBuilder queryBuilder = new QueryBuilder().append(fromQuery)
                .appendQualifier("WHERE", conditionQueryBuilder.getQuery())
                .appendQualifier("GROUP BY", QueryBuilder.join(",", groupByList))
                .appendQualifier("HAVING", having.getQuery())
                .appendQualifier("ORDER BY", QueryBuilder.join(",", orderByList));

        if (limit > VALUE_UNSET) {
            queryBuilder.appendQualifier("LIMIT", String.valueOf(limit));
        }
        if (offset > VALUE_UNSET) {
            queryBuilder.appendQualifier("OFFSET", String.valueOf(offset));
        }

        return queryBuilder.getQuery();
    }

    /**
     * @return the result of the query as a {@link Cursor}.
     */
    @Override
    public Cursor query() {
        // Query the sql here
        Cursor cursor = null;
        String query = getQuery();
        if (whereBase.getQueryBuilderBase() instanceof Select) {
            cursor = databaseDefinition.getWritableDatabase()
                    .rawQuery(query, null);
        } else {
            databaseDefinition.getWritableDatabase()
                    .execSQL(query);
        }

        return cursor;
    }

    @Override
    public void queryClose() {
        Cursor query = query();
        if (query != null) {
            query.close();
        }
    }

    /**
     * Queries for all of the results this statement returns from a DB cursor in the form of the {@link ModelClass}
     *
     * @return All of the entries in the DB converted into {@link ModelClass}
     */
    @Override
    public List<ModelClass> queryList() {
        checkSelect("query");
        return super.queryList();
    }

    protected void checkSelect(String methodName) {
        if (!(whereBase.getQueryBuilderBase() instanceof Select)) {
            throw new IllegalArgumentException("Please use " + methodName + "(). The beginning is not a Select");
        }
    }

    /**
     * Queries and returns only the first {@link ModelClass} result from the DB. Will enforce a limit of 1 item
     * returned from the database.
     *
     * @return The first result of this query. Note: this query forces a limit of 1 from the database.
     */
    @Override
    public ModelClass querySingle() {
        checkSelect("query");
        limit(1);
        return super.querySingle();
    }

    /**
     * Returns whether the DB {@link android.database.Cursor} returns with a count of at least 1
     *
     * @return if {@link android.database.Cursor}.count &lt; 0
     */
    public boolean hasData() {
        checkSelect("query");
        return SqlUtils.hasData(whereBase.getTable(), getQuery());
    }
}