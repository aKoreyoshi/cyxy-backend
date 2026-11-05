package com.cyxy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyxy.exception.CYXYException;
import com.cyxy.mapper.ProductMapper;
import com.cyxy.model.dto.ProductQueryDTO;
import com.cyxy.model.entity.Product;
import com.cyxy.model.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author: Koreyoshi
 * @version: 1.0
 * @date: 2026年11月02日, 22:09:26
 */

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final SizeTierService sizeTierService;

    /**
     * 统一时区：北京时间。
     * 显式指定，不依赖 JVM 默认时区——换机器、换容器部署都不会判错「提前/晚送达」
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 排序白名单：前端 sortField → 数据库列名，防 SQL 注入。按列表页实际 prop 补齐
     */
    private static final Map<String, String> SORT_COLUMNS = Map.of("product_no", "product_no", "name_cn", "name_cn", "purchase_price", "purchase_price", "list_price_usd", "list_price_usd", "created_time", "created_time", "updated_time", "updated_time");

    // 创建新商品
    public Product createProduct(Product product) {
        checkProductNo(product.getProductNo(), null);
        normalize(product);
        productMapper.insert(product);           // 自增 id 会回填到 p
        return product;
    }

    // 更新产品信息
    public Product updateProduct(Product product) {
        // 产品不存在，直接抛出异常
        if (product.getId() == null) {
            throw new CYXYException("缺少产品ID");
        }
        if (productMapper.selectById(product.getId()) == null) {
            throw new CYXYException("产品不存在或已经删除");
        }
        // 取库里当前的配送字段做比对。
        // ⚠️ 必须走 selectOne(wrapper) 而不是 selectById：MyBatis 一级缓存会在同一个
        // SqlSession 里把上一次 selectById 的**对象实例**直接返回，那样 exists 与传入的
        // product 是同一个引用，「用库值覆盖」就变成了自己给自己赋值，锁定会静默失效
        Product exists = productMapper.selectOne(new LambdaQueryWrapper<Product>().eq(Product::getId, product.getId()).select(Product::getActualDeliveryTime, Product::getExpectedDeliveryDate, Product::getDeliveryStatus, Product::getDelayDays));
        checkProductNo(product.getProductNo(), product.getId());

        // 已确认送达 → 配送相关字段锁定。
        // 详情页是「整条表单全量提交」，所以这里不抛异常（否则改个备注都存不了），
        // 而是用库里的值覆盖前端传来的，前端控件本来就禁用了，这里只是后端兜底
        if (exists != null && exists.getActualDeliveryTime() != null) {
            product.setExpectedDeliveryDate(exists.getExpectedDeliveryDate());
            product.setActualDeliveryTime(exists.getActualDeliveryTime());
            product.setDeliveryStatus(exists.getDeliveryStatus());
            product.setDelayDays(exists.getDelayDays());
        }

        normalize(product);

        // MP 默认 NOT_NULL 策略：null 列不更新，所以「清空可选字段」暂时不生效。
        // 要支持的话改成：productMapper.update(null, new LambdaUpdateWrapper<Product>()
        //     .eq(Product::getId, p.getId()).set(Product::getRemark, p.getRemark()));
        productMapper.updateById(product);

        // 还没确认送达时，「清空预计送达时间」要能真正清掉：
        // updateById 是 NOT_NULL 策略，传 null 的字段会被跳过，所以这里用 wrapper 显式同步一次
        if (product.getActualDeliveryTime() == null) {
            String status = product.getExpectedDeliveryDate() == null ? DeliveryStatus.NOT_SET.getCode() : DeliveryStatus.IN_TRANSIT.getCode();
            productMapper.update(null, new LambdaUpdateWrapper<Product>().eq(Product::getId, product.getId()).set(Product::getExpectedDeliveryDate, product.getExpectedDeliveryDate()).set(Product::getDeliveryStatus, status).set(Product::getDelayDays, null));
        }

        return productMapper.selectById(product.getId());
    }

    // 删除产品信息
    public void removeProduct(Long id) {
        productMapper.deleteById(id);
    }

    /**
     * 确认送达。
     * <p>
     * 时间由服务端按北京时间生成，不接收前端传值（防篡改、也避免客户端时区不准）；
     * 判定按自然日：实际送达日期 − 预计送达日期，负数提前 / 0 准时 / 正数晚送达。
     * 确认后 expected_delivery_date 锁定，如需修改走 cancelDelivery。
     *
     * @return 更新后的产品（含新的配送状态与天数差）
     */
    public Product confirmDelivery(Long id) {
        Product product = requireProduct(id);
        if (product.getExpectedDeliveryDate() == null) {
            throw new CYXYException("请先填写预计送达时间");
        }
        if (product.getActualDeliveryTime() != null) {
            throw new CYXYException("已确认送达，不能重复确认");
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        int days = (int) ChronoUnit.DAYS.between(product.getExpectedDeliveryDate(), now.toLocalDate());
        DeliveryStatus status = DeliveryStatus.judge(days);

        // 只更这 3 个字段：走 wrapper 显式 set，避免把整个实体回写（main_image 等长字段）
        productMapper.update(null, new LambdaUpdateWrapper<Product>().eq(Product::getId, id).set(Product::getActualDeliveryTime, now).set(Product::getDelayDays, days).set(Product::getDeliveryStatus, status.getCode()));

        return productMapper.selectById(id);
    }

    /**
     * 撤销确认送达：清空实际送达时间与天数差，状态退回「配送中」（没填预计时间则退回「未填写」）。
     * 这是修正误操作的唯一入口——确认后不能直接改预计送达时间。
     *
     * @return 更新后的产品
     */
    public Product cancelDelivery(Long id) {
        Product product = requireProduct(id);
        if (product.getActualDeliveryTime() == null) {
            throw new CYXYException("尚未确认送达，无需撤销");
        }

        String backTo = product.getExpectedDeliveryDate() == null ? DeliveryStatus.NOT_SET.getCode() : DeliveryStatus.IN_TRANSIT.getCode();

        productMapper.update(null, new LambdaUpdateWrapper<Product>().eq(Product::getId, id).set(Product::getActualDeliveryTime, null).set(Product::getDelayDays, null).set(Product::getDeliveryStatus, backTo));

        return productMapper.selectById(id);
    }

    /**
     * 按 id 取产品，不存在直接抛异常
     */
    private Product requireProduct(Long id) {
        if (id == null) throw new CYXYException("缺少产品 ID");
        Product product = productMapper.selectById(id);
        if (product == null) throw new CYXYException("产品不存在或已删除");
        return product;
    }

    // 获取产品详情
    public Product showDetails(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) throw new CYXYException("产品不存在或已删除");
        return product;
    }

    public Map<String, Object> productList(ProductQueryDTO queryParam) {
        int pageNumber = queryParam.getPageNumber() == null || queryParam.getPageNumber() < 1 ? 1 : queryParam.getPageNumber();
        int pageSize = queryParam.getPageSize() == null || queryParam.getPageSize() < 1 ? 10 : queryParam.getPageSize();

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>().like(StringUtils.hasText(queryParam.getNameCn()), Product::getNameCn, queryParam.getNameCn()).like(StringUtils.hasText(queryParam.getProductNo()), Product::getProductNo, queryParam.getProductNo()).eq(StringUtils.hasText(queryParam.getCategoryCode()), Product::getCategoryCode, queryParam.getCategoryCode()).eq(StringUtils.hasText(queryParam.getStatus()), Product::getStatus, queryParam.getStatus());

        // ⚠️ SORT_COLUMNS 是 Map.of(...)，它的 get(null) 会直接抛 NPE（不是返回 null），
        // 而「不排序」是列表页的默认情况，所以这里必须先挡一道
        String column = queryParam.getSortField() == null ? null : SORT_COLUMNS.get(queryParam.getSortField());
        if (column != null) {
            boolean asc = !"descending".equals(queryParam.getSortOrder());
            wrapper.last("ORDER BY " + column + (asc ? " ASC" : " DESC"));
        } else {
            wrapper.orderByDesc(Product::getId);
        }

        Page<Product> productPage = productMapper.selectPage(Page.of(pageNumber, pageSize), wrapper);
        // 分页壳用 Map：key 不受 Jackson 命名策略影响，能原样保住 camelCase 的 pageNumber
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", productPage.getRecords());
        data.put("total", productPage.getTotal());
        data.put("pageNumber", pageNumber);
        data.put("pageSize", pageSize);

        return data;
    }

    // 判断产品是否存在(根据产品编号)
    private void checkProductNo(String no, Long excludeId) {
        if (!StringUtils.hasText(no)) throw new CYXYException("产品编号不能为空");
        Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getProductNo, no).ne(excludeId != null, Product::getId, excludeId));
        if (count != null && count > 0) throw new CYXYException("产品编号已存在：" + no);
    }

    /**
     * 落库前兜底：尺寸分段重算 + 两个 NOT NULL 字段的临时处理
     */
    private void normalize(Product product) {
        String tier = sizeTierService.resolve(product.getLengthCm(), product.getWidthCm(), product.getHeightCm(), product.getPackagedWeightG());
        if (tier != null) product.setSizeTierCode(tier);   // 不信前端传的，防篡改/漏传

        // list_price_usd 库里 NOT NULL 且无默认值，但前端没列必填 → 补 0
        if (product.getListPriceUsd() == null) product.setListPriceUsd(BigDecimal.ZERO);

        // main_image 现在存的是 COS 短 URL（约 120 字符），这段兜底正常不会触发。
        // 保留是为了兼容库里可能残留的历史 dataURL 脏数据：删掉的话，编辑保存时
        // varchar(512) 会被超长字符串撑爆，整条 SQL 报错，连其它字段也存不进去
        if (product.getMainImage() != null && product.getMainImage().length() > 512) {
            product.setMainImage("");
        }

        // 配送状态：只要还没确认送达，就跟随「有没有填预计送达时间」自动维护。
        // 已确认的终态由 confirmDelivery 写入，这里不碰
        if (product.getActualDeliveryTime() == null) {
            product.setDeliveryStatus(product.getExpectedDeliveryDate() == null ? DeliveryStatus.NOT_SET.getCode() : DeliveryStatus.IN_TRANSIT.getCode());
        }
    }

}