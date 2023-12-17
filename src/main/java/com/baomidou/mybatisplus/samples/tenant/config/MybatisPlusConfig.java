package com.baomidou.mybatisplus.samples.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author miemie
 * @since 2018-08-10
 */
@Configuration
@MapperScan("com.baomidou.mybatisplus.samples.tenant.mapper")
public class MybatisPlusConfig {

    private static String TEST_1 = "com.baomidou.mybatisplus.samples.tenant.mapper.UserMapper.selectList";
    private static String TEST_2 = "com.baomidou.userMapper.selectById";
    private static String TEST_3 = "com.baomidou.roleMapper.selectByCompanyId";
    private static String TEST_4 = "com.baomidou.roleMapper.selectById";
    private static String TEST_5 = "com.baomidou.roleMapper.selectByRoleId";

    private static Map<String, String> sqlSegmentMap = new HashMap<String, String>() {
        {
            put(TEST_1, "name like 'jack%'");
            put(TEST_2, "u.state=1 and u.amount > 1000");
            put(TEST_3, "companyId in (1,2,3)");
            put(TEST_4, "name like 'jack%'");
            put(TEST_5, "id=1 and role_id in (select id from sys_role)");
        }
    };
    /**
     * 新多租户插件配置,一缓和二缓遵循mybatis的规则,需要设置 MybatisConfiguration#useDeprecatedExecutor = false 避免缓存万一出现问题
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(1);
            }

            // 这是 default 方法,默认返回 false 表示所有表都需要拼多租户条件
            @Override
            public boolean ignoreTable(String tableName) {
                return !"sys_user".equalsIgnoreCase(tableName);
            }
        }));
        // 如果用了分页插件注意先 add TenantLineInnerInterceptor 再 add PaginationInnerInterceptor
        // 用了分页插件必须设置 MybatisConfiguration#useDeprecatedExecutor = false
//        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new DataPermissionHandler() {

            @Override
            public Expression getSqlSegment(Expression where, String mappedStatementId) {
                try {
                    String sqlSegment = sqlSegmentMap.get(mappedStatementId);
                    if (StringUtils.hasText(sqlSegment)){
                        Expression sqlSegmentExpression = CCJSqlParserUtil.parseCondExpression(sqlSegment);
                        if (null != where) {
                            System.out.println("原 where : " + where.toString());
                            if (mappedStatementId.equals(TEST_4)) {
                                // 这里测试返回 OR 条件
                                return new OrExpression(where, sqlSegmentExpression);
                            }
                            return new AndExpression(where, sqlSegmentExpression);
                        }
                        return sqlSegmentExpression;
                    }
                    return where;
                } catch (JSQLParserException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }));
        return interceptor;
    }

//    @Bean
//    public ConfigurationCustomizer configurationCustomizer() {
//        return configuration -> configuration.setUseDeprecatedExecutor(false);
//    }
}
