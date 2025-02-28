/*
 * (c) 2016 Abil'I.T. http://abilit.eu/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.scada_lts.mango.service;

import com.serotonin.db.IntValuePair;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.PointValueDao;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.serotonin.mango.rt.dataImage.SetPointSource;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.event.type.AuditEventType;
import com.serotonin.mango.rt.event.type.AuditEventUtils;
import com.serotonin.mango.vo.DataPointExtendedNameComparator;
import com.serotonin.mango.vo.DataPointVO;
import com.serotonin.mango.vo.User;
import com.serotonin.mango.vo.bean.PointHistoryCount;
import com.serotonin.mango.view.text.NoneRenderer;
import com.serotonin.mango.vo.dataSource.DataSourceVO;
import com.serotonin.mango.vo.event.PointEventDetectorVO;
import com.serotonin.mango.vo.hierarchy.PointFolder;
import com.serotonin.mango.vo.hierarchy.PointHierarchy;
import com.serotonin.mango.vo.link.PointLinkVO;
import com.serotonin.mango.vo.permission.DataPointAccess;
import com.serotonin.mango.vo.permission.PermissionException;
import com.serotonin.mango.vo.permission.Permissions;
import com.serotonin.util.Tuple;
import org.apache.commons.logging.LogFactory;
import org.jfree.util.Log;
import org.quartz.SchedulerException;
import org.scada_lts.cache.EventDetectorsCache;
import org.scada_lts.config.ScadaConfig;
import org.scada_lts.dao.*;
import org.scada_lts.dao.model.point.PointValue;
import org.scada_lts.dao.pointhierarchy.PointHierarchyDAO;
import org.scada_lts.dao.PointLinkDAO;
import org.scada_lts.dao.pointvalues.PointValueAmChartDAO;
import org.scada_lts.dao.pointvalues.PointValueDAO;
import org.scada_lts.dao.pointvalues.PointValueDAO4REST;
import org.scada_lts.dao.watchlist.WatchListDAO;
import org.scada_lts.mango.adapter.MangoDataPoint;
import org.scada_lts.mango.adapter.MangoPointHierarchy;
import org.scada_lts.permissions.service.GetDataPointsWithAccess;
import org.scada_lts.permissions.service.GetObjectsWithAccess;
import org.scada_lts.service.pointhierarchy.PointHierarchyService;
import org.scada_lts.web.beans.ApplicationBeans;
import org.scada_lts.web.mvc.api.AggregateSettings;
import org.scada_lts.web.mvc.api.dto.PointValueDTO;
import org.scada_lts.web.mvc.api.json.JsonBinaryEventTextRenderer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;

import static org.scada_lts.utils.AggregateUtils.*;

/**
 * Service for DataPointDAO
 *
 * @author Mateusz Kaproń Abil'I.T. development team, sdt@abilit.eu
 */
@Service
public class DataPointService implements MangoDataPoint {

	private static final org.apache.commons.logging.Log LOG = LogFactory.getLog(DataPointService.class);

	private final DataPointDAO dataPointDAO;

	private final DataSourceDAO dataSourceDAO;

	private final IUserCommentDAO userCommentDAO;

	private static final PointEventDetectorDAO pointEventDetectorDAO = new PointEventDetectorDAO();

	private final PointHierarchyDAO pointHierarchyDAO;

	private final DataPointUserDAO dataPointUserDAO ;

	private static final PointValueDAO pointValueDAO = new PointValueDAO();

	private final WatchListDAO watchListDAO;

	private static final PointLinkDAO pointLinkDAO = new PointLinkDAO();

	private final PointHierarchyService pointHierarchyService;

	private static final PointValueAmChartDAO pointValueAmChartDao = new PointValueAmChartDAO();

	private final GetObjectsWithAccess<DataPointVO, User> getDataPointsWithAccess;

	public DataPointService() {
		this.dataPointDAO = ApplicationBeans.getBean("dataPointDAO", DataPointDAO.class);
		this.dataSourceDAO = ApplicationBeans.getBean("dataSourceDAO", DataSourceDAO.class);
		this.pointHierarchyDAO =  ApplicationBeans.getBean("pointHierarchyDAO", PointHierarchyDAO.class);
		this.dataPointUserDAO = ApplicationBeans.getBean("dataPointUserDAO", DataPointUserDAO.class);
		this.watchListDAO = ApplicationBeans.getBean("watchListDAO", WatchListDAO.class);
		this.pointHierarchyService = ApplicationBeans.getBean("pointHierarchyService", PointHierarchyService.class);
		this.userCommentDAO = ApplicationBeans.getUserCommentDaoBean();
		this.getDataPointsWithAccess = new GetDataPointsWithAccess(dataPointDAO);
	}

	@Override
	public String generateUniqueXid() {
		return DAO.getInstance().generateUniqueXid(DataPointVO.XID_PREFIX, "dataPoints");
	}

	@Override
	public boolean isXidUnique(String xid, int excludeId) {
		return DAO.getInstance().isXidUnique(xid, excludeId, "dataPoints");
	}

	@Override
	public String getExtendedPointName(int dataPointId) {
		DataPointVO dataPoint = getDataPoint(dataPointId);
		if (dataPoint == null) {
			return "?";
		}
		return dataPoint.getExtendedName();
	}

	@Override
	public DataPointVO getDataPoint(String xId) {
		DataPointVO dp = dataPointDAO.getDataPoint(xId);
		setRelationalData(dp);
		return dp;
	}
	@Override
	public DataPointVO getDataPointByXid(String xid) {
		DataPointVO dp = dataPointDAO.getDataPoint(xid);
		return dp;
	}
	@Override
	public DataPointVO getDataPoint(int id) {
		DataPointVO dp = dataPointDAO.getDataPoint(id);
		setRelationalData(dp);
		return dp;
	}

	@Override
	public List<DataPointVO> getDataPoints(Comparator<DataPointVO> comparator, boolean includeRelationalData) {
		List<DataPointVO> dpList = dataPointDAO.getDataPoints();
		if (includeRelationalData) {
			setRelationalData(dpList);
		}
		if (comparator != null) {
			Collections.sort(dpList, comparator);
		}
		return dpList;
	}

	@Override
	public List<DataPointVO> getDataPoints(int dataSourceId, Comparator<DataPointVO> comparator) {
		List<DataPointVO> dpList = dataPointDAO.getDataPoints(dataSourceId);
		setRelationalData(dpList);
		if (comparator != null) {
			Collections.sort(dpList, comparator);
		}
		return dpList;
	}

	/**
	 * Get PointList with permission validation
	 *
	 * Get All DataPoints with specific access permission.
	 * To validate that you need to provide a user that should be
	 * checked and the access type.
	 *
	 * @param user - User to validate
	 * @return - Filtered list of DataPoints object
	 */
	@Override
	public List<DataPointVO> getDataPointsWithAccess(User user) {
		return getDataPointsWithAccess.getObjectsWithAccess(user);
	}

	public Map<DataPointVO, List<PointValue>> getDataPoints(String partOfNameDS, String typeDS, String partOfNamePoint, Date startTime, Date endTime) {

		List<DataPointVO> listAllPoints = new LinkedList<>();
		List<DataSourceVO<?>>  listDs = dataSourceDAO.getDataSourceBaseOfName(partOfNameDS);
		for (DataSourceVO<?> ds : listDs) {
			if (ds.getType().name().equals(typeDS.toUpperCase().trim())) {
				List<DataPointVO> dpList = dataPointDAO.getDataPoints(ds.getId());
				listAllPoints.addAll(dpList);
			}
		}

		List<DataPointVO> listPointToGetData = new LinkedList<DataPointVO>();
		for (DataPointVO dpVO : listAllPoints) {
			if (dpVO.getName().contains(partOfNamePoint)) {
				listPointToGetData.add(dpVO);
			}
		}
		Map<DataPointVO, List<PointValue>> pointsWithListData = new HashMap<>();

		for (DataPointVO dp : listPointToGetData) {
			List<PointValue> data = pointValueDAO.filtered(
					" dataPointId = ? and ts > ? and ts < ?",
					 new Object[]{dp.getId(),
					 startTime.getTime(),
					 endTime.getTime()},
					0 );
			pointsWithListData.put(dp,data);
		}

		return pointsWithListData;
	}

	public List<DataPointVO> searchDataPoints(String[] keywords) {
		return dataPointDAO.getDataPointByKeyword(keywords);
	}

	public List<DataPointVO> getPlcDataPoints(int dataSourceId) {
		List<DataPointVO> datapointList = dataPointDAO.getPlcDataPoints(dataSourceId);
		return datapointList;
	}

	public List<PointValueDTO> valuesPointBooleanBaseOnNameFilter2DTO(Map<DataPointVO, List<PointValue>> values) {
		List<PointValueDTO> result = new LinkedList<>();
		for (Map.Entry<DataPointVO, List<PointValue>> entry : values.entrySet()) {
			DataPointVO dpVO = entry.getKey();

			List<PointValue> values4DpVO = entry.getValue();
			for (PointValue pv : values4DpVO) {
				result.add(new PointValueDTO(
						pv.getPointValue().getTime(),
						dpVO.getName(),
						pv.getPointValue().getValue().toString()
				));
			}
		}
		return result;
	}

	public void save(User user, String value, String xid, int pointValueType) {
		DataPointVO dpvo = dataPointDAO.getDataPoint(xid);
		new PointValueDAO4REST().save(value, pointValueType, dpvo.getId());
		setPoint(user, dpvo, value);
	}

	private void setPoint(User user, DataPointVO point, String valueStr) {
		Permissions.ensureDataPointSetPermission(user, point);
		setPointImpl(point, valueStr, user);

	}

	public void saveAPI(User user, String value, String xid) {
		DataPointVO dpvo = dataPointDAO.getDataPoint(xid);
		Permissions.ensureDataPointSetPermission(user, dpvo);
		setPointImpl(dpvo, value, user);
	}

	private void setPointImpl(DataPointVO point, String valueStr, SetPointSource source) {
		if (point == null)
			return;

		if (valueStr == null)
			Common.ctx.getRuntimeManager().relinquish(point.getId());
		else {
			// Convert the string value into an object.
			MangoValue value = MangoValue.stringToValue(valueStr, point.getPointLocator().getDataTypeId());
			Common.ctx.getRuntimeManager().setDataPointValue(point.getId(), value, source);
		}
	}

	private void setRelationalData(List<DataPointVO> dpList) {
		for (DataPointVO dp: dpList) {
			setRelationalData(dp);
		}
	}

	private void setRelationalData(DataPointVO dp) {
		if (dp != null) {
			setEventDetectors(dp);
			setPointComments(dp);
		}
	}

	public void updateDataPointConfiguration(DataPointVO dp) {
		if(dp.getId() != Common.NEW_ID) {
			DataPointVO existingDataPoint = getDataPoint(dp.getId());
			existingDataPoint.setName(dp.getName());
			existingDataPoint.setXid(dp.getXid());
			existingDataPoint.setDescription(dp.getDescription());
			existingDataPoint.setEnabled(dp.isEnabled());
			existingDataPoint.setPointLocator(dp.getPointLocator());
			updateAndInitializeDataPoint(existingDataPoint);
		}
	}

	public void updateAndInitializeDataPoint(DataPointVO dp) {
		Common.ctx.getRuntimeManager().saveDataPoint(dp);
	}

	public void createDataPointConfiguration(DataPointVO dp) {
		if(dp.getId() == Common.NEW_ID) {
			dp.setEventDetectors(new ArrayList<>());
			createDataPoint(dp);
		}
	}

	@Override
	public void saveDataPoint(final DataPointVO dp) {
		if (dp.getId() == -1) {
			insertDataPoint(dp);
			PointHierarchyDAO.cachedPointHierarchy = null;
			MangoPointHierarchy.getInst().addDataPoint(dp);
			AuditEventUtils.raiseAddedEvent(AuditEventType.TYPE_DATA_POINT, dp);
		} else {
			updateDataPoint(dp);
			MangoPointHierarchy.getInst().updateDataPoint(dp);
		}
	}

	@Override
	public void insertDataPoint(final DataPointVO dp) {
		//Create default text renderer
		if (dp.getTextRenderer() == null) {
			dp.defaultTextRenderer();
		}

		dp.setId(dataPointDAO.insert(dp));
		saveEventDetectors(dp);
	}

	@Override
	public void updateDataPoint(final DataPointVO dp) {
		DataPointVO oldDp = dataPointDAO.getDataPoint(dp.getId());
		if (oldDp.getPointLocator().getDataTypeId() != dp.getPointLocator().getDataTypeId()) {
			new PointValueDao().deletePointValuesWithMismatchedType(dp.getId(), dp.getPointLocator().getDataTypeId());
		}

		updateDataPointShallow(dp);
		saveEventDetectors(dp);
		AuditEventUtils.raiseChangedEvent(AuditEventType.TYPE_DATA_POINT, oldDp, dp);
	}

	@Override
	public void updateDataPointShallow(final DataPointVO dp) {
		dataPointDAO.update(dp);
	}

	@Override
	public void deleteDataPoint(int dataPointId) {
		try {
			//Note: See class DataSourceEditDWR::deletePoint
			Common.ctx.getEventManager().cancelEventsForDataPoint(dataPointId);
			beforePointDelete(dataPointId);
			deletePointHistory(dataPointId);
			deleteDataPointImpl(Integer.toString(dataPointId));
			UsersProfileService ups = new UsersProfileService();
			ups.updateDataPointPermissions();
		} catch (EmptyResultDataAccessException e) {
			Log.error(e);
			return;
		}
	}

	public void deleteDataPoint(String dataPointXid) {
		DataPointVO dp = getDataPoint(dataPointXid);
		deleteDataPoint(dp.getId());
	}

	@Override
	public void deleteDataPoints(int dataSourceId) {
		List<DataPointVO> oldList = getDataPoints(dataSourceId, null);
		for (DataPointVO dp: oldList) {
			beforePointDelete(dp.getId());
		}
		for (DataPointVO dp: oldList) {
			deletePointHistory(dp.getId());
		}

		List<Integer> pointIds = dataPointDAO.getDataPointsIds(dataSourceId);
		if (pointIds.size() > 0) {
			StringBuilder idsWithCommaSB = new StringBuilder();
			Iterator idsIterator = pointIds.iterator();
			idsWithCommaSB.append(pointIds.get(0));
			while (idsIterator.hasNext()) {
				idsWithCommaSB.append(",");
				idsWithCommaSB.append(idsIterator.next());
			}

			deleteDataPointImpl(idsWithCommaSB.toString());
		}
	}

	private void beforePointDelete(int dpId) {
		for (PointLinkVO link: pointLinkDAO.getPointLinksForPoint(dpId)) {
			Common.ctx.getRuntimeManager().deletePointLink(link.getId());
		}
	}

	@Override
	public void deletePointHistory(int dpId) {
		//long min = pointValueDAO.getMinTs(dpId);
		//long max = pointValueDAO.getMaxTs(dpId);
		deletePointHistory(dpId, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public void deletePointHistory(int dpId, long min, long max) {
		while (true) {
			try {
				pointValueDAO.deletePointValuesBeforeWithOutLastTwo(dpId, max);
				break;
			} catch (UncategorizedSQLException e) {
                if ("The total number of locks exceeds the lock table size".equals(e.getSQLException().getMessage())) {
                    long mid = (min + max) >> 1;
                    deletePointHistory(dpId, min, mid);
                    min = mid;
                }
                else
                    throw e;
            }
		}
	}

	//TODO rewrite int[] dataPointIds 
	@Override
	public void deleteDataPointImpl(String dataPointIds) {

		dataPointDAO.deleteEventHandler(dataPointIds);
		userCommentDAO.deleteUserCommentPoint(dataPointIds);
		pointEventDetectorDAO.deleteWithId(dataPointIds);
		for (String id: dataPointIds.split(",")){
			dataPointUserDAO.deleteWhereDataPointId(Integer.valueOf(id));
		}
		watchListDAO.deleteWatchListPoints(dataPointIds);
		dataPointDAO.deleteWithIn(dataPointIds);
		UsersProfileService usersProfileService = new UsersProfileService();
		usersProfileService.updateDataPointPermissions();
		usersProfileService.updateWatchlistPermissions();
		PointHierarchyDAO.cachedPointHierarchy = null;
		MangoPointHierarchy.getInst().deleteDataPoint(dataPointIds);
	}

	@Override
	public int getDataPointIdFromDetectorId(int pointEventDetectorId) {
		return pointEventDetectorDAO.getDataPointId(pointEventDetectorId);
	}

	@Override
	public String getDetectorXid(int pointEventDetectorId) {
		return pointEventDetectorDAO.getXid(pointEventDetectorId);
	}

	@Override
	public int getDetectorId(String pointEventDetectorXid, int dataPointId) {
		return pointEventDetectorDAO.getId(pointEventDetectorXid, dataPointId);
	}

	@Override
	public String generateEventDetectorUniqueXid(int dataPointId) {
		String xid = DAO.getInstance().generateXid(PointEventDetectorVO.XID_PREFIX);
		while (!isEventDetectorXidUnique(dataPointId, xid, -1)) {
			xid = DAO.getInstance().generateXid(PointEventDetectorVO.XID_PREFIX);
		}
		return xid;
	}

	@Override
	public boolean isEventDetectorXidUnique(int dataPointId, String xid, int excludeId) {
		return pointEventDetectorDAO.isEventDetectorXidUnique(dataPointId, xid, excludeId);
	}

	private void setEventDetectors(DataPointVO dataPoint) {
		dataPoint.setEventDetectors(getEventDetectors(dataPoint));
	}

	public List<PointEventDetectorVO> getEventDetectors(DataPointVO dataPoint) {

		EventDetectorsCache.LOG.trace("getEventDetectors() dpId:" + dataPoint.getId());
		long startTime = 0;
		if (EventDetectorsCache.LOG.isTraceEnabled()) {
			startTime = System.currentTimeMillis();
		}

		List<PointEventDetectorVO> result = null;
		try {
			boolean cacheEnable = ScadaConfig.getInstance().getBoolean(ScadaConfig.ENABLE_CACHE, false);
			if (cacheEnable) {
				result = EventDetectorsCache.getInstance().getEventDetectors(dataPoint);
			} else {
				result = pointEventDetectorDAO.getPointEventDetectors(dataPoint);
			}
		} catch (SchedulerException | IOException e) {
			EventDetectorsCache.LOG.error(e);
		}

		long endTime = 0;
		if (EventDetectorsCache.LOG.isTraceEnabled()) {
			endTime = System.currentTimeMillis();
		}
		EventDetectorsCache.LOG.trace("TimeExecute:"+(endTime-startTime)+ " getEventDetectors() dpId:"+dataPoint.getId());

		return result;
	}

	public void saveEventDetectors(DataPointVO dataPoint) {
		List<PointEventDetectorVO> detectors = getEventDetectors(dataPoint);

		for (PointEventDetectorVO pointEventDetector: detectors) {
			if(!dataPoint.getEventDetectors().contains(pointEventDetector)) {
				pointEventDetectorDAO.delete(dataPoint.getId(), pointEventDetector.getId());
			}
		}
		
		for (PointEventDetectorVO pointEventDetector: dataPoint.getEventDetectors()) {
			try {
			    pointEventDetectorDAO.insert(pointEventDetector);
			} catch (DuplicateKeyException e) {
				pointEventDetectorDAO.update(pointEventDetector);
			}
		}
	}

	public void updateEventDetectorWithType(PointEventDetectorVO eventDetector) {
		pointEventDetectorDAO.updateWithType(eventDetector);
	}

	public void deleteEventDetector(DataPointVO dataPoint, int id){
		pointEventDetectorDAO.delete(dataPoint.getId(), id);
	}

	private PointEventDetectorVO removeFromList(List<PointEventDetectorVO> list, int id) {
		for (PointEventDetectorVO ped : list) {
			if (ped.getId() == id) {
				list.remove(ped);
				return ped;
			}
		}
		return null;
	}

	@Override
	public void copyPermissions(final int fromDataPointId, final int toDataPointId) {

		final List<Tuple<Integer,Integer>> ups = dataPointUserDAO.getDataPointUsers(fromDataPointId);
		dataPointUserDAO.insert(ups, toDataPointId);
	}

	private void setPointComments(DataPointVO dp) {
		dp.setComments(userCommentDAO.getPointComments(dp));
	}

	@Override
	public void savePointsInFolder(PointFolder folder) {

		// Save the points in the subfolders
		for (PointFolder sf : folder.getSubfolders()) {
			savePointsInFolder(sf);
		}

		// Update the folder references in the points.
		DataPointVO dp;
		for (IntValuePair p : folder.getPoints()) {
			dp = getDataPoint(p.getKey());
			// The point may have been deleted while editing the hierarchy.
			if (dp != null) {
				dp.setPointFolderId(folder.getId());
				dataPointDAO.update(dp);
			}
		}
	}

	@Override
	public PointHierarchy getPointHierarchy() {
		if (PointHierarchyDAO.cachedPointHierarchy == null) {
			final Map<Integer, List<PointFolder>> folders = pointHierarchyDAO.getFolderList();

			PointHierarchy ph = new PointHierarchy();
			pointHierarchyService.addFoldersToHierarchy(ph, 0 ,folders);

			List<DataPointVO> points = getDataPoints(DataPointExtendedNameComparator.instance, false);
			for (DataPointVO dataPoint: points) {
				ph.addDataPoint(dataPoint.getId(), dataPoint.getPointFolderId(), dataPoint.getExtendedName());
			}

			PointHierarchyDAO.cachedPointHierarchy = ph;
		}
		return PointHierarchyDAO.cachedPointHierarchy;
	}

	@Override
	public void savePointHierarchy(PointFolder root) {
		pointHierarchyService.savePointHierarchy(root);
	}

	@Override
	public void savePointFolder(PointFolder folder, int parentId) {
		pointHierarchyService.savePointFolder(folder, parentId);
	}

	public List<PointHistoryCount> getTopPointHistoryCounts() {
		List<PointHistoryCount> counts = pointValueDAO.getTopPointHistoryCounts();
		List<DataPointVO> points = getDataPoints(DataPointExtendedNameComparator.instance, false);

		for (PointHistoryCount c : counts) {
			for (DataPointVO point : points) {
				if (point.getId() == c.getPointId()) {
					c.setPointName(point.getExtendedName());
					break;
				}
			}
		}

		return counts;
	}

	public JsonBinaryEventTextRenderer getBinaryEventTextRenderer(DataPointVO dataPointVO, int value) {
		JsonBinaryEventTextRenderer json = new JsonBinaryEventTextRenderer();
		if (value == 0) {
			json.setEventText(dataPointVO.getEventTextRenderer().getText(false));
		} else if (value == 1) {
			json.setEventText(dataPointVO.getEventTextRenderer().getText(true));
		}
		return json;
	}

	public List<Map<String, Double>> getPointValuesFromRange(List<DataPointVO> pointIds, long startTs, long endTs,
															 AggregateSettings aggregateSettings) {
		if (pointIds.isEmpty())
			return Collections.emptyList();
		if (aggregateSettings.isEnabled()) {
			return pointValueAmChartDao.convertToAmChartDataObject(aggregateValuesFromRange(startTs, endTs, pointIds, aggregateSettings));
		}
		return pointValueAmChartDao.getPointValuesFromRange(getPointIds(pointIds), startTs, endTs);
	}

	public List<Map<String, Double>> getPointValuesToCompareFromRange(List<DataPointVO> dataPoints, long startTs, long endTs,
																	  AggregateSettings aggregateSettings) {
		if(dataPoints.isEmpty())
			return Collections.emptyList();
		if (aggregateSettings.isEnabled()) {
			return pointValueAmChartDao.convertToAmChartCompareDataObject(aggregateValuesFromRange(startTs, endTs, dataPoints, aggregateSettings), dataPoints.get(0).getId());
		}
		return pointValueAmChartDao.getPointValuesToCompareFromRange(getPointIds(dataPoints), startTs, endTs);
	}

    public List<DataPointVO> getDataPoints(Set<Integer> pointIds) {
        return pointIds.stream()
                .map(a -> getDataPointOpt(a))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.peek(a -> {
					if(a.getPointLocator() == null) {
						LOG.warn(PointValueAmChartDAO.dataPointInfo(a));
					}
				})
				.filter(a -> a.getPointLocator() != null)
                .collect(Collectors.toList());
    }

	public List<DataPointVO> getDataPointsByXid(Set<String> xids) {
		List<DataPointVO> pointIds = new ArrayList<>();
		for(String xid: xids) {
			DataPointVO dataPoint = getDataPoint(xid);
			if(dataPoint != null)
				pointIds.add(dataPoint);
			else
				LOG.warn("datapoint does not exist for xid: " + xid);
		}
		return pointIds;
	}

	private List<PointValueAmChartDAO.DataPointSimpleValue> aggregateValuesFromRange(long startTs, long endTs,
																					 List<DataPointVO> pointIds,
																					 AggregateSettings aggregateSettings) {
		int limit = aggregateSettings.getValuesLimit();
		List<PointValueAmChartDAO.DataPointSimpleValue> pvcList = pointValueAmChartDao
				.getPointValuesFromRangeWithLimit(getPointIds(pointIds), startTs, endTs, limit + 1);
		if (pvcList.size() > limit) {
			pvcList.clear();
			long intervalMs = calculateIntervalMs(startTs, endTs, pointIds.size(), aggregateSettings);
			int revisedLimit = calculateLimit(aggregateSettings);
			long revisedStartTs = calculateStartTs(startTs, intervalMs);
			return aggregateSortValues(revisedStartTs, endTs, pointIds, revisedLimit, intervalMs);
		}
		return pvcList;
	}

	private int[] getPointIds(List<DataPointVO> pointIds) {
		return pointIds.stream().mapToInt(DataPointVO::getId).toArray();
	}

	private List<PointValueAmChartDAO.DataPointSimpleValue> aggregateSortValues(long startTs, long endTs,
																				List<DataPointVO> dataPoints,
																				int limit, long intervalMs) {
		return dataPoints.stream()
				.flatMap(dataPoint -> pointValueAmChartDao.aggregatePointValues(dataPoint, startTs, endTs, intervalMs,
						limitByDataType(dataPoint, limit)).stream())
				.sorted(Comparator.comparingLong(PointValueAmChartDAO.DataPointSimpleValue::getTimestamp))
				.collect(Collectors.toList());
	}

	private Optional<DataPointVO> getDataPointOpt(Integer a) {
		if(a == null)
			return Optional.empty();
		try {
			return Optional.ofNullable(getDataPoint(a));
		} catch (Exception ex) {
			LOG.error(ex.getMessage());
			return Optional.empty();
		}
	}

	public DataPointVO createDataPoint(DataPointVO dataPoint) {
		dataPoint.setEventDetectors(new ArrayList<>());
		dataPoint.setTextRenderer(new NoneRenderer());
		DataPointVO created = dataPointDAO.create(dataPoint);
		Common.ctx.getRuntimeManager().saveDataPoint(created);
		return created;
	}

	@Override
	public List<DataPointVO> searchDataPointsBy(String searchText) {
		if (searchText != null) {
			String[] keywords = searchText.split("\\s+");
			return searchDataPoints(keywords);
		}
		return getDataPoints(Comparator.comparing(DataPointVO::getName), false);
	}
}
