/**
 * 通用响应结构 - 所有服务（除 auth-service）返回此格式
 */
export interface Result<T = any> {
  /** 业务状态码，200表示成功，其他为错误码 */
  code: number;
  /** 状态描述，成功为"success"，失败为错误描述 */
  message: string;
  /** 业务数据，错误时为null */
  data: T;
}

/**
 * 分页响应结构
 */
export interface PageResult<T = any> {
  /** 数据列表 */
  records: T[];
  /** 总记录数 */
  total: number;
  /** 每页条数 */
  size: number;
  /** 当前页码 */
  current: number;
  /** 总页数（可选） */
  pages?: number;
}

/**
 * 获取应用Token请求参数（form-urlencoded格式）
 */
export interface GetTokenRequest {
  /** 应用标识，需在网关配置中注册 */
  appId: string;
  /** Unix时间戳（秒），有效期5分钟 */
  timestamp: string;
  /** 随机字符串，防止重放攻击 */
  nonce: string;
  /** HMAC-SHA256签名，签名内容: appId:timestamp:nonce */
  sign: string;
}

/**
 * 获取应用Token响应
 */
export interface GetTokenResponse {
  /** JWT访问令牌，有效期2小时 */
  token: string;
}

/**
 * 认证服务类型定义
 */
export namespace AuthService {
  /**
   * 用户注册请求
   */
  export interface RegisterRequest {
    /** 用户名，长度3-32字符，全局唯一 */
    username: string;
    /** 密码，长度8-64字符 */
    password: string;
    /** 邮箱地址（可选） */
    email?: string;
    /** 手机号码（可选） */
    phone?: string;
  }

  /**
   * 用户登录请求
   */
  export interface LoginRequest {
    /** 用户名 */
    username: string;
    /** 密码 */
    password: string;
  }

  /**
   * 用户登录响应
   */
  export interface LoginResponse {
    /** JWT访问令牌，有效期2小时 */
    token: string;
    /** 用户ID */
    userId: number;
    /** 用户名 */
    username: string;
  }

  /**
   * 用户信息响应
   */
  export interface UserInfoResponse {
    /** 用户ID */
    id: number;
    /** 用户名 */
    username: string;
    /** 邮箱（可选） */
    email?: string;
    /** 手机号（可选） */
    phone?: string;
  }
}

/**
 * 房源服务类型定义
 */
export namespace PropertyService {
  /**
   * 房源实体
   */
  export interface Property {
    /** 房源ID（创建时不传，返回时自动生成） */
    id?: number;
    /** 房源标题 */
    title: string;
    /** 房源描述（可选） */
    description?: string;
    /** 价格（单位：元） */
    price: number;
    /** 面积（单位：㎡） */
    area: number;
    /** 城市编码 */
    cityCode: string;
    /** 区县编码（可选） */
    districtCode?: string;
    /** 详细地址（可选） */
    address?: string;
    /** 纬度（可选） */
    latitude?: number;
    /** 经度（可选） */
    longitude?: number;
    /** 卧室数量（可选） */
    bedrooms?: number;
    /** 卫生间数量（可选） */
    bathrooms?: number;
    /** 楼层（可选） */
    floor?: number;
    /** 总楼层（可选） */
    totalFloors?: number;
    /** 房源类型（可选） */
    propertyType?: string;
    /** 发布状态（可选）：0-未发布，1-已发布，2-已下架 */
    publishStatus?: number;
    /** 审核状态（可选）：0-待审核，1-审核通过，2-审核不通过 */
    auditStatus?: number;
    /** 浏览量（可选） */
    viewCount?: number;
    /** 城市名称（可选，查询时返回） */
    cityName?: string;
    /** 区县名称（可选，查询时返回） */
    districtName?: string;
    /** 创建时间（可选） */
    createdAt?: string;
    /** 更新时间（可选） */
    updatedAt?: string;
  }

  /**
   * 创建房源请求
   */
  export interface CreatePropertyRequest extends Property {}

  /**
   * 更新房源请求（所有字段可选）
   */
  export interface UpdatePropertyRequest {
    /** 房源标题（可选） */
    title?: string;
    /** 房源描述（可选） */
    description?: string;
    /** 价格（可选） */
    price?: number;
    /** 面积（可选） */
    area?: number;
    /** 城市编码（可选） */
    cityCode?: string;
    /** 区县编码（可选） */
    districtCode?: string;
    /** 详细地址（可选） */
    address?: string;
    /** 纬度（可选） */
    latitude?: number;
    /** 经度（可选） */
    longitude?: number;
    /** 卧室数量（可选） */
    bedrooms?: number;
    /** 卫生间数量（可选） */
    bathrooms?: number;
    /** 楼层（可选） */
    floor?: number;
    /** 总楼层（可选） */
    totalFloors?: number;
    /** 房源类型（可选） */
    propertyType?: string;
    /** 发布状态（可选） */
    publishStatus?: number;
  }

  /**
   * 查询房源列表参数
   */
  export interface PropertyListQuery {
    /** 页码（可选，默认1） */
    page?: number;
    /** 每页条数（可选，默认20） */
    size?: number;
    /** 城市编码（可选） */
    cityCode?: string;
    /** 区县编码（可选） */
    districtCode?: string;
    /** 最低价格（可选） */
    minPrice?: number;
    /** 最高价格（可选） */
    maxPrice?: number;
    /** 最小面积（可选） */
    minArea?: number;
    /** 最大面积（可选） */
    maxArea?: number;
    /** 卧室数量（可选） */
    bedrooms?: number;
    /** 房源类型（可选） */
    propertyType?: string;
    /** 搜索关键词（可选） */
    keywords?: string;
  }

  /**
   * 房源简要信息（批量查询时返回）
   */
  export interface PropertyBrief {
    /** 房源ID */
    id: number;
    /** 房源标题 */
    title: string;
    /** 封面图URL（可选） */
    coverUrl?: string;
    /** 城市名称 */
    cityName: string;
  }

  /**
   * 更新发布状态请求
   */
  export interface UpdatePublishStatusRequest {
    /** 发布状态：0-未发布，1-已发布 */
    publishStatus: number;
  }

  /**
   * 更新审核状态请求
   */
  export interface UpdateAuditStatusRequest {
    /** 审核状态：0-待审核，1-审核通过，2-审核不通过 */
    status: number;
  }

  /**
   * 批量查询房源简要信息参数
   */
  export interface PropertyBriefQuery {
    /** 房源ID列表，逗号分隔 */
    ids: string;
  }

  /**
   * 发布状态常量
   */
  export const PublishStatus = {
    /** 未发布 */
    UNPUBLISHED: 0,
    /** 已发布 */
    PUBLISHED: 1,
    /** 已下架 */
    OFFLINE: 2
  };

  /**
   * 审核状态常量
   */
  export const AuditStatus = {
    /** 待审核 */
    PENDING: 0,
    /** 审核通过 */
    APPROVED: 1,
    /** 审核不通过 */
    REJECTED: 2
  };

  /**
   * 房源类型枚举
   */
  export type PropertyType = 'residential' | 'apartment' | 'commercial' | 'villa';

  /**
   * 户型枚举
   */
  export type RoomType = 'one_room' | 'two_room' | 'three_room' | 'four_room';
}

/**
 * 搜索服务类型定义
 */
export namespace SearchService {
  /**
   * 房源搜索请求参数
   */
  export interface PropertySearchRequest {
    /** 关键词搜索（可选） */
    keyword?: string;
    /** 城市编码（可选） */
    cityCode?: string;
    /** 房源类型（可选） */
    type?: string;
    /** 户型（可选） */
    rooms?: string;
    /** 最低价格（可选） */
    minPrice?: number;
    /** 最高价格（可选） */
    maxPrice?: number;
    /** 是否热门（可选） */
    hot?: boolean;
    /** 是否精选（可选） */
    featured?: boolean;
    /** 排序字段（可选，默认createdAt） */
    sortBy?: string;
    /** 排序方向（可选，默认desc） */
    sortDirection?: string;
    /** 中心点纬度（可选，附近搜索） */
    centerLat?: number;
    /** 中心点经度（可选，附近搜索） */
    centerLng?: number;
    /** 搜索半径（可选，单位：公里） */
    radiusKm?: number;
    /** 页码（可选，默认1） */
    page?: number;
    /** 每页条数（可选，默认20） */
    size?: number;
  }

  /**
   * 搜索结果中的房源文档
   */
  export interface PropertyDocument {
    /** 房源ID */
    id: number;
    /** 应用标识（可选） */
    appId?: string;
    /** 房源标题 */
    title: string;
    /** 房源类型（可选） */
    type?: string;
    /** 价格（单位：元） */
    price: number;
    /** 面积（单位：㎡） */
    rentalArea: number;
    /** 户型（可选） */
    rooms?: string;
    /** 朝向（可选） */
    orientation?: string;
    /** 楼层描述（可选） */
    floor?: string;
    /** 详细地址（可选） */
    address?: string;
    /** 纬度（可选） */
    lat?: number;
    /** 经度（可选） */
    lon?: number;
    /** 省份名称（可选） */
    provinceName?: string;
    /** 城市名称（可选） */
    cityName?: string;
    /** 区县名称（可选） */
    districtName?: string;
    /** 装修情况（可选） */
    decoration?: string;
    /** 房源描述（可选） */
    description?: string;
    /** 是否热门（可选） */
    hot?: boolean;
    /** 是否精选（可选） */
    featured?: boolean;
    /** 封面图URL（可选） */
    coverUrl?: string;
    /** 距离（可选，附近搜索时返回，单位：公里） */
    distance?: number;
  }

  /**
   * 搜索结果
   */
  export interface SearchResult {
    /** 房源列表 */
    records: PropertyDocument[];
    /** 总记录数 */
    total: number;
    /** 每页条数 */
    size: number;
    /** 当前页码 */
    current: number;
    /** 总页数 */
    pages: number;
  }

  /**
   * 排序字段枚举
   */
  export type SortBy = 'createdAt' | 'price' | 'area' | 'viewCount';

  /**
   * 排序方向枚举
   */
  export type SortDirection = 'asc' | 'desc';
}

/**
 * 收藏服务类型定义
 */
export namespace FavoriteService {
  /**
   * 收藏记录
   */
  export interface Favorite {
    /** 收藏记录ID */
    favoriteId: number;
    /** 房源ID */
    propertyId: number;
    /** 房源标题（可选，从property-service获取） */
    title?: string;
    /** 房源封面图URL（可选，从property-service获取） */
    coverUrl?: string;
    /** 城市名称（可选，从property-service获取） */
    cityName?: string;
    /** 收藏时间（可选） */
    createdTime?: string;
  }

  /**
   * 查询收藏列表参数
   */
  export interface FavoriteListQuery {
    /** 页码（可选，默认1） */
    page?: number;
    /** 每页条数（可选，默认20） */
    size?: number;
  }

  /**
   * 检查是否已收藏响应
   */
  export interface CheckFavoriteResponse {
    /** true-已收藏，false-未收藏 */
    data: boolean;
  }
}

/**
 * 审核服务类型定义
 */
export namespace ReviewService {
  /**
   * 审核任务
   */
  export interface AuditTask {
    /** 审核任务ID */
    id: number;
    /** 房源ID */
    propertyId: number;
    /** 应用ID */
    appId: string;
    /** 任务类型：MACHINE-机器审核，MANUAL-人工审核 */
    taskType: string;
    /** 状态：0-待处理，1-处理中，2-通过，3-拒绝，4-异常 */
    status: number;
    /** 审核结果详情JSON（可选） */
    resultDetail?: string;
    /** 创建时间（可选） */
    createdAt?: string;
    /** 更新时间（可选） */
    updatedAt?: string;
  }

  /**
   * 查询审核任务列表参数
   */
  export interface AuditTaskListQuery {
    /** 状态筛选（可选） */
    status?: number;
    /** 页码（可选，默认1） */
    page?: number;
    /** 每页数量（可选，默认10） */
    size?: number;
  }

  /**
   * 人工审核请求
   */
  export interface ManualAuditRequest {
    /** 审核任务ID */
    taskId: number;
    /** 审核结果：1-通过，2-拒绝 */
    result: number;
    /** 审核原因 */
    reason: string;
    /** 审核员ID */
    auditorId: number;
  }

  /**
   * 任务类型枚举
   */
  export type TaskType = 'MACHINE' | 'MANUAL';

  /**
   * 审核状态常量
   */
  export const AuditStatus = {
    /** 待处理 */
    PENDING: 0,
    /** 处理中 */
    PROCESSING: 1,
    /** 通过 */
    APPROVED: 2,
    /** 拒绝 */
    REJECTED: 3,
    /** 异常 */
    ERROR: 4
  };

  /**
   * 审核结果常量
   */
  export const AuditResult = {
    /** 通过 */
    APPROVE: 1,
    /** 拒绝 */
    REJECT: 2
  };
}

/**
 * 预约服务类型定义
 */
export namespace BookingService {
  /**
   * 预约记录
   */
  export interface Booking {
    /** 预约ID */
    id: number;
    /** 房源ID */
    propertyId: number;
    /** 房源标题（可选，从property-service获取） */
    propertyTitle?: string;
    /** 房源封面图URL（可选，从property-service获取） */
    propertyCover?: string;
    /** 预约时间（ISO格式） */
    appointmentTime: string;
    /** 状态：0-待确认，1-已确认，2-已完成，3-已取消，4-已拒绝 */
    status: number;
    /** 状态描述 */
    statusDesc: string;
    /** 用户留言（可选） */
    remark?: string;
    /** 创建时间（可选） */
    createdAt?: string;
  }

  /**
   * 创建预约请求
   */
  export interface CreateBookingRequest {
    /** 房源ID（必填） */
    propertyId: number;
    /** 预约时间（必填，ISO格式，必须是将来时间） */
    appointmentTime: string;
    /** 用户留言（可选，最多500字） */
    remark?: string;
  }

  /**
   * 用户取消预约请求
   */
  export interface CancelBookingRequest {
    /** 取消原因（必填） */
    reason: string;
  }

  /**
   * 经纪人拒绝预约请求
   */
  export interface RejectBookingRequest {
    /** 拒绝原因（必填） */
    reason: string;
  }

  /**
   * 查询预约列表参数
   */
  export interface BookingListQuery {
    /** 页码（可选，默认1） */
    page?: number;
    /** 每页条数（可选，默认20） */
    size?: number;
  }

  /**
   * 获取预约详情参数
   */
  export interface GetBookingDetailRequest {
    /** 用户角色（可选，agent表示经纪人） */
    role?: string;
  }

  /**
   * 预约状态常量
   */
  export const BookingStatus = {
    /** 待确认 */
    PENDING: 0,
    /** 已确认 */
    CONFIRMED: 1,
    /** 已完成 */
    COMPLETED: 2,
    /** 已取消 */
    CANCELED: 3,
    /** 已拒绝 */
    REJECTED: 4
  };

  /**
   * 预约状态枚举
   */
  export type BookingStatusType = 0 | 1 | 2 | 3 | 4;

  /**
   * 用户角色枚举
   */
  export type UserRole = 'user' | 'agent';

  /**
   * 预约通知模板编码
   */
  export const BookingTemplateCode = {
    /** 新预约创建通知（通知经纪人） */
    BOOKING_CREATE_NOTIFY: 'BOOKING_CREATE_NOTIFY',
    /** 预约确认通知（通知用户） */
    BOOKING_CONFIRM_NOTIFY: 'BOOKING_CONFIRM_NOTIFY',
    /** 预约取消通知（通知经纪人） */
    BOOKING_CANCEL_NOTIFY: 'BOOKING_CANCEL_NOTIFY',
    /** 预约拒绝通知（通知用户） */
    BOOKING_REJECT_NOTIFY: 'BOOKING_REJECT_NOTIFY',
    /** 看房完成通知（通知用户） */
    BOOKING_COMPLETE_NOTIFY: 'BOOKING_COMPLETE_NOTIFY',
  } as const;

  /**
   * 预约通知参数
   */
  export interface BookingNotificationParams {
    /** 预约ID */
    bookingId: number;
    /** 房源ID */
    propertyId: number;
    /** 用户ID */
    userId: number;
    /** 经纪人ID */
    agentId?: number;
    /** 预约时间 */
    appointmentTime?: string;
    /** 用户留言 */
    remark?: string;
    /** 取消原因 */
    cancelReason?: string;
    /** 拒绝原因 */
    rejectReason?: string;
    /** 操作时间（创建/确认/取消/拒绝/完成） */
    [key: string]: string | number | undefined;
  }
}

/**
 * 字典服务类型定义
 */
export namespace DictService {
  /**
   * 区域基础项
   */
  export interface RegionItem {
    /** 区域代码 */
    regionCode: string;
    /** 区域名称 */
    regionName: string;
    /** 区域类型（可选） */
    regionType?: string;
    /** 省份代码（可选） */
    provinceCode?: string;
    /** 省份名称（可选） */
    provinceName?: string;
    /** 城市代码（可选） */
    cityCode?: string;
    /** 城市名称（可选） */
    cityName?: string;
    /** 区县代码（可选） */
    districtCode?: string;
    /** 区县名称（可选） */
    districtName?: string;
    /** 排序值（可选） */
    sortOrder?: number;
  }

  /**
   * 省份
   */
  export interface Province extends RegionItem {}

  /**
   * 城市
   */
  export interface City extends RegionItem {}

  /**
   * 区县
   */
  export interface District extends RegionItem {}

  /**
   * 乡镇/街道
   */
  export interface Town extends RegionItem {
    /** 乡镇代码（可选） */
    townCode?: string;
    /** 乡镇名称（可选） */
    townName?: string;
  }

  /**
   * 村/社区
   */
  export interface Village extends Town {
    /** 区域代码 */
    regionCode: string;
    /** 区域名称 */
    regionName: string;
    /** 区域类型 */
    regionType: string;
    /** 乡镇代码 */
    townCode: string;
    /** 乡镇名称 */
    townName: string;
  }

  /**
   * 区域完整路径
   */
  export interface RegionPath {
    /** 区域代码 */
    regionCode: string;
    /** 区域名称 */
    regionName: string;
    /** 上级代码（可选） */
    parentCode?: string;
    /** 层级（1-省/直辖市，2-地级市，3-区/县，4-乡镇/街道，5-村/社区） */
    regionLevel: number;
    /** 区域类型 */
    regionType: string;
    /** 完整名称路径，如"广东省 > 深圳市 > 南山区" */
    fullPath: string;
    /** 完整代码路径，如"440000/440300/440305" */
    codePath: string;
    /** 当前节点在树中的深度（从省份=0开始） */
    depth: number;
  }

  /**
   * 国家/地区
   */
  export interface Country {
    /** ISO 3166-1 二位字母代码 */
    countryCode: string;
    /** ISO 3166-1 三位字母代码 */
    countryCode3: string;
    /** ISO 3166-1 三位数字代码 */
    numericCode: string;
    /** 中文名称 */
    nameZh: string;
    /** 英文名称 */
    nameEn: string;
    /** 国际电话区号 */
    phoneCode: string;
    /** 货币代码 */
    currencyCode: string;
    /** 大洲代码（如AS、EU、NA） */
    continentCode: string;
    /** 国旗Emoji */
    flagEmoji: string;
    /** 排序值 */
    sortOrder: number;
  }

  /**
   * 货币
   */
  export interface Currency {
    /** ISO 4217 货币代码 */
    currencyCode: string;
    /** 中文名称 */
    nameZh: string;
    /** 英文名称 */
    nameEn: string;
    /** 货币符号 */
    symbol: string;
    /** 状态（1-启用，0-停用） */
    status: number;
  }

  /**
   * 语言
   */
  export interface Language {
    /** ISO 639-2b 三位语言代码 */
    langCode: string;
    /** 中文名称 */
    nameZh: string;
    /** 英文名称 */
    nameEn: string;
    /** 母语名称 */
    nativeName: string;
  }

  /**
   * 时区
   */
  export interface Timezone {
    /** 时区标识符（IANA格式） */
    timezoneId: string;
    /** UTC偏移量 */
    offsetUtc: string;
    /** 描述说明 */
    description: string;
  }

  /**
   * 普通字典项
   */
  export interface DictItem {
    /** 字典代码 */
    code: string;
    /** 字典名称 */
    name: string;
    /** 排序值（可选） */
    sortOrder?: number;
    /** 状态（可选，1-启用，0-停用） */
    status?: number;
  }

  /**
   * 树形字典项
   */
  export interface TreeDictItem extends DictItem {
    /** 父级代码（可选，根节点为null） */
    parentCode?: string;
    /** 层级（可选） */
    level?: number;
  }

  /**
   * 房产字典项
   */
  export interface PropertyDictItem {
    /** 字典项键值 */
    itemKey: string;
    /** 字典项值 */
    itemValue: string;
    /** 排序值（可选） */
    sortOrder?: number;
    /** 状态（可选，1-启用，0-停用） */
    status?: number;
  }

  /**
   * 房产字典类型
   */
  export interface PropertyDictType {
    /** 字典类型编码 */
    typeCode: string;
    /** 字典类型名称 */
    typeName: string;
    /** 备注说明（可选） */
    remark?: string;
  }

  /**
   * 查询城市列表参数
   */
  export interface CityQuery {
    /** 省份代码（可选，不传则返回全部） */
    provinceCode?: string;
  }

  /**
   * 查询区县列表参数
   */
  export interface DistrictQuery {
    /** 城市代码（可选，不传则返回全部） */
    cityCode?: string;
  }

  /**
   * 查询乡镇列表参数
   */
  export interface TownQuery {
    /** 区县代码（可选，不传则返回全部） */
    districtCode?: string;
  }

  /**
   * 查询村/社区列表参数
   */
  export interface VillageQuery {
    /** 乡镇代码（可选，不传则返回全部） */
    townCode?: string;
  }

  /**
   * 查询区域路径参数
   */
  export interface RegionPathQuery {
    /** 区域代码（可选） */
    regionCode?: string;
    /** 层级（可选，1-5） */
    regionLevel?: number;
  }

  /**
   * 查询国家列表参数
   */
  export interface CountryQuery {
    /** 大洲代码（可选） */
    continentCode?: string;
    /** 名称关键字（可选，中英文均可） */
    keyword?: string;
  }

  /**
   * 查询货币列表参数
   */
  export interface CurrencyQuery {
    /** 状态（可选，1-启用，0-停用） */
    status?: number;
    /** 关键字（可选，匹配中文名、英文名、货币代码） */
    keyword?: string;
  }

  /**
   * 查询语言列表参数
   */
  export interface LanguageQuery {
    /** 关键字（可选，匹配中文名、英文名、语言代码） */
    keyword?: string;
  }

  /**
   * 查询时区列表参数
   */
  export interface TimezoneQuery {
    /** 关键字（可选，匹配时区ID、描述） */
    keyword?: string;
  }

  /**
   * 查询普通字典列表参数
   */
  export interface DictListQuery {
    /** 状态（可选，1-启用，0-停用） */
    status?: number;
    /** 关键字（可选，匹配name和code） */
    keyword?: string;
  }

  /**
   * 查询树形字典列表参数
   */
  export interface TreeDictQuery {
    /** 父级代码（可选，不传则返回全部） */
    parentCode?: string;
    /** 层级（可选） */
    level?: number;
    /** 关键字（可选，匹配name和code） */
    keyword?: string;
  }

  /**
   * 查询房产字典项参数
   */
  export interface PropertyDictQuery {
    /** 字典类型编码（必填） */
    type: string;
    /** 状态（可选，1-启用，0-停用） */
    status?: number;
    /** 关键字（可选，匹配itemKey和itemValue） */
    keyword?: string;
  }

  /**
   * 大洲代码枚举
   */
  export type ContinentCode = 'AF' | 'AS' | 'EU' | 'NA' | 'OC' | 'SA' | 'AN';

  /**
   * 房产字典类型枚举
   */
  export type PropertyDictTypeCode = 
    | 'property_type'
    | 'decoration'
    | 'heating_method'
    | 'orientation'
    | 'room_type'
    | 'publish_status'
    | 'audit_status';

  /**
   * 装修类型枚举
   */
  export type DecorationType = 'rough' | 'simple' | 'fine' | 'luxury';

  /**
   * 朝向类型枚举
   */
  export type OrientationType = 
    | 'south'
    | 'north'
    | 'east'
    | 'west'
    | 'southeast'
    | 'northeast'
    | 'southwest'
    | 'northwest';
}

/**
 * 图片服务类型定义
 */
export namespace ImageService {
  /**
   * 图片信息
   */
  export interface Image {
    /** 图片ID */
    id: number;
    /** 默认图片URL（原图） */
    url: string;
    /** 原图URL（WebP格式） */
    originUrl: string;
    /** 大图URL（宽度1280px） */
    largeUrl: string;
    /** 中图URL（宽度640px） */
    mediumUrl: string;
    /** 小图URL（宽度200px） */
    smallUrl: string;
    /** 原图宽度（像素） */
    width: number;
    /** 原图高度（像素） */
    height: number;
    /** 原图大小（字节） */
    fileSize: number;
    /** MIME类型，统一为image/webp */
    mimeType: string;
  }

  /**
   * 上传图片请求
   */
  export interface ImageUploadRequest {
    /** 图片文件 */
    file: File;
  }

  /**
   * 查询图片列表参数
   */
  export interface ImageListQuery {
    /** 页码（可选，默认1） */
    page?: number;
    /** 每页数量（可选，默认20） */
    size?: number;
  }

  /**
   * 删除图片请求
   */
  export interface ImageDeleteRequest {
    /** 图片ID */
    id: number;
  }

  /**
   * 图片分组
   */
  export interface ImageGroup {
    /** 分组ID */
    id: number;
    /** 应用标识 */
    appId: string;
    /** 分组名称 */
    name: string;
    /** 分组描述（可选） */
    description?: string;
    /** 排序号（可选） */
    sortOrder?: number;
    /** 创建时间（可选） */
    createdAt?: string;
  }

  /**
   * 创建分组请求
   */
  export interface CreateGroupRequest {
    /** 分组名称（同一应用内唯一） */
    name: string;
    /** 分组描述（可选） */
    description?: string;
  }

  /**
   * 添加图片到分组请求
   */
  export interface AddImageToGroupRequest {
    /** 图片ID */
    imageId: number;
    /** 排序号（可选，默认0） */
    sortOrder?: number;
  }

  /**
   * 获取分组图片参数
   */
  export interface GroupImagesQuery {
    /** 图片尺寸类型（可选，默认large） */
    sizeType?: SizeType;
  }

  /**
   * 获取分组图片响应
   */
  export interface GroupImagesResponse {
    /** 分组ID */
    id: number;
    /** 分组名称 */
    name: string;
    /** 分组描述（可选） */
    description?: string;
    /** 排序号（可选） */
    sortOrder?: number;
    /** 图片列表 */
    images: Image[];
  }

  /**
   * 图片尺寸类型
   */
  export type SizeType = 'original' | 'large' | 'medium' | 'small';
}

/**
 * 统计服务类型定义
 */
export namespace AnalyticsService {
  /**
   * 数据看板总览
   */
  export interface DashboardSummary {
    /** 统计日期（今日） */
    today: string;
    /** 今日房产浏览总量（PV） */
    todayPropertyViews: number;
    /** 今日图片上传总量 */
    todayImageUploads: number;
    /** 今日用户注册数 */
    todayUserRegisters: number;
    /** 今日用户登录数 */
    todayUserLogins: number;
    /** 今日房产发布数 */
    todayPropertyCreates: number;
    /** 热门房产TOP10 */
    topProperties: PropertyViewStats[];
    /** 各应用图片上传汇总 */
    appImageSummary: ImageUploadSummary[];
  }

  /**
   * 房产浏览统计
   */
  export interface PropertyViewStats {
    /** 应用标识 */
    appId: string;
    /** 房产ID */
    propertyId: number;
    /** 统计日期 */
    statsDate: string;
    /** 浏览量（PV） */
    viewCount: number;
    /** 独立访客数（UV） */
    uniqueVisitors: number;
  }

  /**
   * 查询房产浏览统计参数
   */
  export interface PropertyViewsQuery {
    /** 应用标识（必填） */
    appId: string;
    /** 开始日期（必填，格式yyyy-MM-dd） */
    startDate: string;
    /** 结束日期（可选，格式yyyy-MM-dd，默认当天） */
    endDate?: string;
  }

  /**
   * 图片上传统计
   */
  export interface ImageUploadSummary {
    /** 应用标识 */
    appId: string;
    /** 统计日期 */
    statsDate: string;
    /** 上传图片数量（张） */
    uploadCount: number;
    /** 总存储大小（字节） */
    totalSize: number;
    /** 格式化后的大小（如"15.00 MB"） */
    totalSizeFormatted: string;
  }

  /**
   * 查询图片上传统计参数
   */
  export interface ImageUploadSummaryQuery {
    /** 应用标识（可选，不传则返回所有应用） */
    appId?: string;
  }

  /**
   * 用户行为统计
   */
  export interface UserActionStats {
    /** 应用标识 */
    appId: string;
    /** 事件类型 */
    eventType: string;
    /** 统计日期 */
    statsDate: string;
    /** 行为发生次数 */
    actionCount: number;
  }

  /**
   * 查询用户行为统计参数
   */
  export interface UserActionsQuery {
    /** 应用标识（可选） */
    appId?: string;
    /** 事件类型（可选） */
    eventType?: string;
    /** 开始日期（可选，格式yyyy-MM-dd，默认当天） */
    startDate?: string;
    /** 结束日期（可选，格式yyyy-MM-dd，默认当天） */
    endDate?: string;
  }

  /**
   * 事件类型枚举
   */
  export type EventType = 
    | 'PROPERTY_CREATE'    // 房产发布
    | 'IMAGE_DELETE'       // 图片删除
    | 'USER_REGISTER'      // 用户注册
    | 'USER_LOGIN'         // 用户登录
    | 'FAVORITE_ADD'       // 收藏房产
    | 'FAVORITE_REMOVE';   // 取消收藏
}

/**
 * 消息服务类型定义
 */
export namespace MessageService {
  /**
   * 发送测试邮件请求
   */
  export interface SendTestRequest {
    /** 接收测试邮件的邮箱地址 */
    email: string;
  }

  /**
   * 通过模板发送消息请求
   */
  export interface SendMessageRequest {
    /** 来源应用ID（可选） */
    appId?: string;
    /** 模板编码（必填，需在数据库中配置且启用） */
    templateCode: string;
    /** 接收地址（邮箱/手机号） */
    receiver: string;
    /** 消息渠道（可选，EMAIL或SMS，默认从模板获取） */
    channel?: MessageChannel;
    /** 邮件主题（可选，优先级高于模板中配置的主题） */
    subject?: string;
    /** 模板参数（可选，用于替换占位符${paramName}） */
    params?: Record<string, string>;
    /** 追踪ID（可选，用于链路追踪） */
    traceId?: string;
  }

  /**
   * 消息记录
   */
  export interface MessageRecord {
    /** 记录ID */
    id: number;
    /** 来源应用ID */
    appId: string;
    /** 消息类型：EMAIL或SMS */
    messageType: MessageChannel;
    /** 接收地址 */
    receiver: string;
    /** 最终发送的内容 */
    content: string;
    /** 状态：PENDING-待发送，SUCCESS-成功，FAILED-失败 */
    status: MessageStatus;
    /** 已重试次数 */
    retryCount: number;
    /** 错误信息（失败时有值） */
    errorMessage?: string;
    /** 创建时间（可选） */
    createdAt?: string;
    /** 更新时间（可选） */
    updatedAt?: string;
  }

  /**
   * 查询消息记录列表参数
   */
  export interface MessageRecordsQuery {
    /** 状态筛选（可选，0-待发送，1-成功，2-失败） */
    status?: number;
    /** 页码（可选，默认1） */
    page?: number;
    /** 每页条数（可选，默认20） */
    size?: number;
  }

  /**
   * 消息重试请求
   */
  export interface RetryMessageRequest {
    /** 消息记录ID */
    id: number;
  }

  /**
   * 消息渠道类型
   */
  export type MessageChannel = 'EMAIL' | 'SMS';

  /**
   * 消息状态类型
   */
  export type MessageStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

  /**
   * 消息状态码常量
   */
  export const MessageStatusCode = {
    /** 待发送 */
    PENDING: 0,
    /** 成功 */
    SUCCESS: 1,
    /** 失败 */
    FAILED: 2
  };

  /**
   * 消息模板代码枚举
   */
  export type TemplateCode = 
    | 'PROPERTY_CREATE_NOTIFY'      // 房源创建通知
    | 'PROPERTY_AUDIT_NOTIFY'      // 房源审核通知
    | 'USER_REGISTER_WELCOME'      // 用户注册欢迎
    | 'USER_RESET_PASSWORD';       // 用户重置密码
}