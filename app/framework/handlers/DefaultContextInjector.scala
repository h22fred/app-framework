package framework.handlers

trait DefaultContextInjector{

	//-----------------------------------------------------------------------------------
	// Injection of common services into the scala templates
	// WARNING: this assumes that these services have been injected into the Http.Context
	// otherwise the values will be null.
	// Please see framework.handlers.AbstractRequestHandler
	//------------------------------------------------------------------------------------
	val _messagesPluginService=play.mvc.Http.Context.current().args.get(classOf[framework.services.configuration.II18nMessagesPlugin].getName).asInstanceOf[framework.services.configuration.II18nMessagesPlugin]
	val _cfg=play.mvc.Http.Context.current().args.get(classOf[play.Configuration].getName).asInstanceOf[play.Configuration]
}