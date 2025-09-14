#####
# 
# This class is part of the Programming the Internet of Things
# project, and is available via the MIT License, which can be
# found in the LICENSE file at the top level of this repository.
# 
# You may find it more helpful to your design to adjust the
# functionality, constants and interfaces (if there are any)
# provided within in order to meet the needs of your specific
# Programming the Internet of Things project.
# 

import logging

from apscheduler.schedulers.background import BackgroundScheduler

import programmingtheiot.common.ConfigConst as ConfigConst

from programmingtheiot.common.ConfigUtil import ConfigUtil
from programmingtheiot.common.IDataMessageListener import IDataMessageListener

from programmingtheiot.cda.system.SystemCpuUtilTask import SystemCpuUtilTask
from programmingtheiot.cda.system.SystemMemUtilTask import SystemMemUtilTask

from programmingtheiot.data.SystemPerformanceData import SystemPerformanceData

class SystemPerformanceManager(object):
	"""
	Shell representation of class for student implementation.
	
	"""

	def __init__(self):
		configUtil = ConfigUtil()
		self.pollRate = configUtil.getInteger(
			ConfigConst.CONSTRAINED_DEVICE, 
			ConfigConst.POLL_CYCLES_KEY,
			ConfigConst.DEFAULT_POLL_CYCLES
		)
		self.scheduler = None
		self.cpuUtilTask = SystemCpuUtilTask()
		self.memUtilTask = SystemMemUtilTask()
		self.isStarted = False

	def handleTelemetry(self):
		"""
		Handles telemetry collection from system monitoring tasks
		"""
		cpuUtil = self.cpuUtilTask.getTelemetryValue()
		memUtil = self.memUtilTask.getTelemetryValue()

		logging.info(f"CPU utilization: {cpuUtil}%, Memory utilization: {memUtil}%")
		
	def setDataMessageListener(self, listener: IDataMessageListener) -> bool:
		pass
	
	def startManager(self):
		"""
		Starts the SystemPerformanceManager
		"""
		if not self.isStarted:
			logging.info("Starting SystemPerformanceManager...")
            
			self.scheduler = BackgroundScheduler()
			self.scheduler.add_job(
				func=self.handleTelemetry,
				trigger="interval", 
				seconds=self.pollRate,
				id='system_perf_job'
			)
			self.scheduler.start()
			self.isStarted = True
            
			logging.info("SystemPerformanceManager started")
		
	def stopManager(self):
		"""
		Stops the SystemPerformanceManager
		"""
		if self.isStarted:
			logging.info("Stopping SystemPerformanceManager...")
            
			if self.scheduler:
				self.scheduler.shutdown()
                
			self.isStarted = False
			logging.info("SystemPerformanceManager stopped")
